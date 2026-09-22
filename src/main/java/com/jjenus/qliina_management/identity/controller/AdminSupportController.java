package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.audit.service.AuditService;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.MaskingUtils;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.identity.security.CustomUserDetailsService;
import com.jjenus.qliina_management.identity.security.JwtProvider;
import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Support tooling for platform staff:
 *   GET  /api/v1/admin/search                 — global tenant lookup (businesses + users)
 *   POST /api/v1/admin/impersonate/{businessId} — mint a short-lived, audited token
 *                                               for a tenant user (login-as)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSupportController {

    private static final long IMPERSONATION_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuditService auditService;

    // ---------------------------------------------------------------------
    // Global search
    // ---------------------------------------------------------------------

    @GetMapping("/search")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.support.view')
        or hasPermission(null, 'PLATFORM', 'platform.businesses.view')
    """)
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            throw new BusinessException("'q' is required", "VALIDATION_ERROR", "q");
        }
        String term = q.trim();
        String like = "%" + term.toLowerCase() + "%";

        List<Business> businesses = businessRepository.findAll((Specification<Business>) (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("slug")), like)));
            return cb.and(ps.toArray(new Predicate[0]));
        }, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        var usersPage = userRepository.searchTenantUsers(term, PageRequest.of(0, 10));
        boolean maskPii = "SUPPORT_AGENT".equals(dominantPlatformRole());
        List<Map<String, Object>> users = usersPage.getContent().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", maskPii ? MaskingUtils.maskEmail(u.getEmail()) : u.getEmail());
                    m.put("firstName", maskPii ? MaskingUtils.maskName(u.getFirstName()) : u.getFirstName());
                    m.put("lastName", maskPii ? MaskingUtils.maskName(u.getLastName()) : u.getLastName());
                    m.put("enabled", u.getEnabled());
                    m.put("businessId", u.getBusinessId());
                    m.put("roles", u.getRoles().stream().map(ur -> ur.getRole().getName()).toList());
                    return m;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("query", term);
        result.put("businesses", businesses.stream()
                .map(b -> Map.<String, Object>of(
                        "id", b.getId(),
                        "name", b.getName(),
                        "slug", b.getSlug(),
                        "status", b.getStatus() != null ? b.getStatus().name() : "",
                        "plan", b.getPlan() != null ? b.getPlan().name() : ""))
                .toList());
        result.put("users", users);
        return ResponseEntity.ok(result);
    }

    // ---------------------------------------------------------------------
    // Tenant user listing (admin console business detail)
    // ---------------------------------------------------------------------

    @GetMapping("/businesses/{businessId}/users")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.support.view')
        or hasPermission(null, 'PLATFORM', 'platform.businesses.view')
    """)
    public ResponseEntity<List<Map<String, Object>>> tenantUsers(@PathVariable UUID businessId) {
        if (!businessRepository.existsById(businessId)) {
            throw new BusinessException("Business not found", "BUSINESS_NOT_FOUND");
        }
        boolean maskPii = "SUPPORT_AGENT".equals(dominantPlatformRole());
        List<Map<String, Object>> users = userRepository.findAllByBusinessId(businessId).stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", maskPii ? MaskingUtils.maskEmail(u.getEmail()) : u.getEmail());
                    m.put("firstName", maskPii ? MaskingUtils.maskName(u.getFirstName()) : u.getFirstName());
                    m.put("lastName", maskPii ? MaskingUtils.maskName(u.getLastName()) : u.getLastName());
                    m.put("enabled", u.getEnabled());
                    m.put("lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : null);
                    m.put("roles", u.getRoles().stream().map(ur -> ur.getRole().getName()).toList());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(users);
    }

    // ---------------------------------------------------------------------
    // Impersonation (audited, time-boxed)
    // ---------------------------------------------------------------------

    public record ImpersonateRequest(String username) {}

    @PostMapping("/impersonate/{businessId}")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.impersonate')")
    public ResponseEntity<Map<String, Object>> impersonate(
            @PathVariable UUID businessId,
            @RequestBody(required = false) ImpersonateRequest body) {

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        if (business.getStatus() == Business.Status.CANCELLED) {
            throw new BusinessException("Cannot impersonate into a cancelled business", "BUSINESS_CANCELLED");
        }

        User target = resolveTarget(businessId, body != null ? body.username() : null);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", target.getId());
        claims.put("businessId", target.getBusinessId());
        claims.put("permissions", effectivePermissions(target));
        claims.put("impersonatedBy", currentAdminUsername());
        claims.put("impersonation", true);

        String token = jwtProvider.generateToken(claims,
                userDetailsService.loadUserByUsername(target.getUsername()), IMPERSONATION_TTL_MS);

        auditService.logEvent("IMPERSONATION", target.getId(), "IMPERSONATE",
                null, Map.of("targetUsername", target.getUsername(), "businessId", businessId.toString()),
                "Platform staff impersonation session started");

        log.warn("IMPERSONATION: {} -> user {} of business {}",
                currentAdminUsername(), target.getUsername(), businessId);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", target.getId());
        userInfo.put("username", target.getUsername());
        userInfo.put("email", target.getEmail());
        userInfo.put("firstName", target.getFirstName());
        userInfo.put("lastName", target.getLastName());
        userInfo.put("businessId", target.getBusinessId());
        userInfo.put("currentShopId", target.getPrimaryShopId());
        userInfo.put("roles", target.getRoles().stream().map(ur -> ur.getRole().getName()).toList());
        userInfo.put("permissions", claims.get("permissions"));

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", token);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", IMPERSONATION_TTL_MS / 1000);
        response.put("user", userInfo);
        response.put("impersonatorUsername", currentAdminUsername());
        response.put("businessName", business.getName());
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private User resolveTarget(UUID businessId, String username) {
        if (username != null && !username.isBlank()) {
            User u = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException("Target user not found", "USER_NOT_FOUND"));
            if (!businessId.equals(u.getBusinessId())) {
                throw new BusinessException("User does not belong to this business", "USER_BUSINESS_MISMATCH");
            }
            if (!Boolean.TRUE.equals(u.getEnabled())) {
                throw new BusinessException("Target user is disabled", "USER_DISABLED");
            }
            return u;
        }
        // Default: first enabled BUSINESS_ADMIN, else first enabled user
        List<User> admins = userRepository.findByBusinessIdAndRoles(businessId, List.of("BUSINESS_ADMIN"));
        admins = admins.stream().filter(u -> Boolean.TRUE.equals(u.getEnabled())).toList();
        if (!admins.isEmpty()) return admins.get(0);
        return userRepository.findAllByBusinessId(businessId).stream()
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No enabled users in this business", "NO_USERS"));
    }

    private List<String> effectivePermissions(User user) {
        return user.getRoles().stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getName())
                .distinct()
                .toList();
    }

    private String currentAdminUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    /**
     * Returns the caller's most privileged platform role, or READONLY_AUDITOR
     * as a fallback. Used to decide whether tenant PII is masked in support
     * responses (SUPPORT_AGENT gets masked PII, mirrors AdminBusinessController).
     */
    private String dominantPlatformRole() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null) return "READONLY_AUDITOR";
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) return "READONLY_AUDITOR";
        List<String> ordered = List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "BILLING_ADMIN",
                "SUPPORT_AGENT", "READONLY_AUDITOR");
        return user.getRoles().stream()
                .map(ur -> ur.getRole().getName())
                .filter(ordered::contains)
                .findFirst()
                .orElse("READONLY_AUDITOR");
    }
}
