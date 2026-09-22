package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.business.dto.BusinessDTO;
import com.jjenus.qliina_management.business.dto.PlanUsageDTO;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.model.Shop;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.business.service.BusinessErasureService;
import com.jjenus.qliina_management.business.service.BusinessService;
import com.jjenus.qliina_management.business.service.PlanLimitService;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.MaskingUtils;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.AuthAccount;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.model.UserRole;
import com.jjenus.qliina_management.identity.repository.AuthAccountRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform-staff endpoints for viewing and managing businesses.
 *
 * Response shapes are filtered by the caller's platform role:
 * - SUPER_ADMIN / PLATFORM_ADMIN : full BusinessDTO + plan details
 * - BILLING_ADMIN                : name, status, plan, trial (no operational data)
 * - SUPPORT_AGENT                : name, status, shop count, masked customer contacts
 * - READONLY_AUDITOR             : same as full but read-only marker
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/businesses")
@RequiredArgsConstructor
public class AdminBusinessController {

    private final BusinessRepository businessRepository;
    private final PlanLimitService   planLimitService;
    private final UserRepository     userRepository;
    private final BusinessService    businessService;
    private final SubscriptionRepository subscriptionRepository;
    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository       roleRepository;
    private final ShopRepository       shopRepository;
    private final PasswordEncoder      passwordEncoder;
    private final BusinessErasureService businessErasureService;

    // -----------------------------------------------------------------------
    // List all businesses — paginated via PageResponse, filterable
    // -----------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.view')")
    public PageResponse<Object> listBusinesses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication auth) {

        var spec = businessSpec(status, plan, search);
        var page = spec == null
                ? businessRepository.findAll(pageable)
                : businessRepository.findAll(spec, pageable);

        var role = dominantPlatformRole(auth);
        return PageResponse.from(page.map(b -> buildView(b, role)));
    }

    // -----------------------------------------------------------------------
    // Get single business
    // -----------------------------------------------------------------------

    @GetMapping("/{businessId}")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.view')")
    public ResponseEntity<?> getBusiness(
            @PathVariable UUID businessId,
            Authentication auth) {
        Business b = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        return ResponseEntity.ok(buildView(b, dominantPlatformRole(auth)));
    }

    // -----------------------------------------------------------------------
    // Subscription / usage — accessible to billing & audit roles (not support)
    // -----------------------------------------------------------------------

    @GetMapping("/{businessId}/subscription")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.billing.manage')
        or hasPermission(null, 'PLATFORM', 'platform.plans.manage')
        or hasPermission(null, 'PLATFORM', 'platform.audit.view')
    """)
    public ResponseEntity<PlanUsageDTO> getBusinessSubscription(@PathVariable UUID businessId) {
        return ResponseEntity.ok(planLimitService.getUsageSummary(businessId));
    }

    // -----------------------------------------------------------------------
    // Change plan tier
    // -----------------------------------------------------------------------

    @PatchMapping("/{businessId}/plan")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.billing.manage')")
    public ResponseEntity<BusinessDTO> changePlan(
            @PathVariable UUID businessId,
            @RequestBody Map<String, String> body) {
        String tier = body.get("plan");
        if (tier == null) throw new BusinessException("'plan' field is required", "VALIDATION_ERROR", "plan");
        return ResponseEntity.ok(businessService.changePlan(businessId, tier));
    }

    // -----------------------------------------------------------------------
    // Change status (suspend / activate / cancel)
    // -----------------------------------------------------------------------

    /** Valid transitions per current status. Same-status is a no-op (idempotent). */
    private static final Map<Business.Status, List<Business.Status>> STATUS_TRANSITIONS = Map.of(
            Business.Status.TRIAL,     List.of(Business.Status.TRIAL, Business.Status.ACTIVE,
                    Business.Status.SUSPENDED, Business.Status.CANCELLED, Business.Status.ARCHIVED),
            Business.Status.ACTIVE,    List.of(Business.Status.ACTIVE,
                    Business.Status.SUSPENDED, Business.Status.CANCELLED, Business.Status.ARCHIVED),
            Business.Status.SUSPENDED, List.of(Business.Status.SUSPENDED,
                    Business.Status.ACTIVE, Business.Status.CANCELLED, Business.Status.ARCHIVED),
            // ARCHIVED is the soft-archive step before erasure; it can be restored
            // to ACTIVE but never moves on to CANCELLED.
            Business.Status.ARCHIVED,  List.of(Business.Status.ARCHIVED, Business.Status.ACTIVE),
            // CANCELLED is terminal — no transitions out.
            Business.Status.CANCELLED, List.of(Business.Status.CANCELLED));

    /** States that require a human-readable reason (captured by the audit AOP). */
    private static final List<Business.Status> REASON_REQUIRED =
            List.of(Business.Status.SUSPENDED, Business.Status.CANCELLED, Business.Status.ARCHIVED);

    @PatchMapping("/{businessId}/status")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public ResponseEntity<BusinessDTO> changeStatus(
            @PathVariable UUID businessId,
            @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null) throw new BusinessException("'status' field is required", "VALIDATION_ERROR", "status");
        Business.Status status;
        try { status = Business.Status.valueOf(statusStr.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + statusStr, "INVALID_STATUS", "status");
        }
        Business b = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));

        Business.Status current = b.getStatus() != null ? b.getStatus() : Business.Status.TRIAL;
        if (!STATUS_TRANSITIONS.getOrDefault(current, List.of()).contains(status)) {
            throw new BusinessException(
                    "Cannot transition business from " + current + " to " + status,
                    "INVALID_STATUS_TRANSITION", "status");
        }
        // Same-status is an idempotent no-op and needs no reason (business is already there).
        if (status == current) {
            return ResponseEntity.ok(toDTO(b));
        }
        if (REASON_REQUIRED.contains(status) && (body.get("reason") == null || body.get("reason").isBlank())) {
            throw new BusinessException("'reason' is required for " + status + " status", "VALIDATION_ERROR", "reason");
        }

        b.setStatus(status);
        businessRepository.save(b);
        // Reason is intentionally surfaced via AuditAspect args (the PATCH body); log it too.
        log.info("Status changed: businessId={}, newStatus={}, reason={}",
                businessId, status, body.getOrDefault("reason", ""));
        return ResponseEntity.ok(toDTO(b));
    }

    // -----------------------------------------------------------------------
    // Extend / set trial end — syncs Business.trialEndsAt AND Subscription
    // -----------------------------------------------------------------------

    public record ExtendTrialRequest(String trialEndsAt, Integer days) {}

    @PatchMapping("/{businessId}/trial")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.billing.manage')")
    public ResponseEntity<BusinessDTO> extendTrial(
            @PathVariable UUID businessId,
            @RequestBody ExtendTrialRequest body) {

        LocalDateTime trialEndsAt;
        if (body.days() != null && body.days() > 0) {
            trialEndsAt = LocalDateTime.now().plusDays(body.days());
        } else if (body.trialEndsAt() != null && !body.trialEndsAt().isBlank()) {
            try { trialEndsAt = LocalDateTime.parse(body.trialEndsAt()); }
            catch (DateTimeParseException e) {
                throw new BusinessException("'trialEndsAt' must be ISO-8601 datetime", "VALIDATION_ERROR", "trialEndsAt");
            }
        } else {
            throw new BusinessException("Provide 'days' or 'trialEndsAt'", "VALIDATION_ERROR", "trialEndsAt");
        }

        Business b = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        b.setTrialEndsAt(trialEndsAt);
        businessRepository.save(b);

        subscriptionRepository.findByBusinessIdAndStatusNot(businessId, SubscriptionStatus.CANCELED)
                .ifPresent(sub -> {
                    sub.setTrialEndsAt(trialEndsAt);
                    subscriptionRepository.save(sub);
                });

        log.info("Trial extended: businessId={}, trialEndsAt={}", businessId, trialEndsAt);
        return ResponseEntity.ok(toDTO(b));
    }

    // -----------------------------------------------------------------------
    // Edit business contact info
    // -----------------------------------------------------------------------

    public record UpdateBusinessRequest(String name, String email, String phone) {}

    @PutMapping("/{businessId}")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public ResponseEntity<BusinessDTO> updateBusiness(
            @PathVariable UUID businessId,
            @RequestBody UpdateBusinessRequest body) {
        Business b = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        if (body.name() != null && !body.name().isBlank()) b.setName(body.name().trim());
        if (body.email() != null) b.setEmail(body.email().isBlank() ? null : body.email().trim());
        if (body.phone() != null) b.setPhone(body.phone().isBlank() ? null : body.phone().trim());
        businessRepository.save(b);
        return ResponseEntity.ok(toDTO(b));
    }

    // -----------------------------------------------------------------------
    // Create a business on behalf of a customer (manual provisioning)
    // -----------------------------------------------------------------------

    public record CreateBusinessRequest(String name, String email, String phone,
            String plan, Integer trialDays,
            String ownerUsername, String ownerEmail, String ownerFirstName, String ownerLastName) {}

    /** Credentials handed to the platform staff exactly once, at business creation. */
    public record OwnerProvisioned(UUID userId, String username, String email, String temporaryPassword) {}

    public record CreateBusinessResponse(BusinessDTO business, OwnerProvisioned owner) {}

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public ResponseEntity<CreateBusinessResponse> createBusiness(@RequestBody CreateBusinessRequest body) {
        if (body.name() == null || body.name().isBlank()) {
            throw new BusinessException("'name' is required", "VALIDATION_ERROR", "name");
        }
        String baseSlug = body.name().trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (baseSlug.isBlank()) baseSlug = "business";
        String slug = baseSlug;
        int suffix = 2;
        while (businessRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + suffix++;
        }

        Business.Plan tier = Business.Plan.FREE;
        if (body.plan() != null && !body.plan().isBlank()) {
            try { tier = Business.Plan.valueOf(body.plan().toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid plan: " + body.plan(), "INVALID_PLAN", "plan");
            }
        }

        Business business = Business.builder()
                .name(body.name().trim())
                .slug(slug)
                .status(Business.Status.TRIAL)
                .plan(tier)
                .email(body.email())
                .phone(body.phone())
                .trialEndsAt(LocalDateTime.now().plusDays(body.trialDays() != null && body.trialDays() > 0 ? body.trialDays() : 14))
                .build();
        business = businessRepository.save(business);
        log.info("Business created by platform staff: id={}, slug={}", business.getId(), slug);

        OwnerProvisioned owner = provisionOwnerIfRequested(business, body);
        return ResponseEntity.status(201).body(new CreateBusinessResponse(toDTO(business), owner));
    }

    /**
     * When the platform staff opt to provision an owner for the business right away
     * (N-3), create the initial shop + {@code BUSINESS_ADMIN} owner with a temporary
     * password. Mirrors the self-registration flow: shop code uniqueness, owner
     * uniqueness (username/email), AuthAccount + UserRole wired to the business.
     */
    private OwnerProvisioned provisionOwnerIfRequested(Business business, CreateBusinessRequest body) {
        boolean requested = body.ownerUsername() != null && !body.ownerUsername().isBlank();
        if (!requested) return null;

        if (body.ownerEmail() == null || body.ownerEmail().isBlank()
                || body.ownerFirstName() == null || body.ownerFirstName().isBlank()
                || body.ownerLastName() == null || body.ownerLastName().isBlank()) {
            throw new BusinessException(
                    "Owner provisioning requires ownerUsername, ownerEmail, ownerFirstName and ownerLastName",
                    "VALIDATION_ERROR", "owner");
        }
        if (!body.ownerEmail().contains("@")) {
            throw new BusinessException("Owner email looks invalid", "VALIDATION_ERROR", "ownerEmail");
        }
        if (userRepository.existsByUsername(body.ownerUsername())) {
            throw new BusinessException("Username already taken", "USERNAME_EXISTS", "ownerUsername");
        }
        if (userRepository.existsByEmail(body.ownerEmail())) {
            throw new BusinessException("Email already registered", "EMAIL_EXISTS", "ownerEmail");
        }

        // Initial shop (UserRole.shop_id is NOT NULL, so an owner needs a shop).
        String rawCode = business.getSlug().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String upperCode = rawCode.isEmpty() ? "SHOP" : rawCode;
        if (shopRepository.existsByCode(upperCode)) {
            int n = 1;
            while (shopRepository.existsByCode(upperCode + n)) n++;
            upperCode = upperCode + n;
        }
        Shop shop = new Shop();
        shop.setBusinessId(business.getId());
        shop.setName(business.getName());
        shop.setCode(upperCode);
        shop.setActive(true);
        shop = shopRepository.save(shop);

        final Shop finalShop = shop;
        User user = new User();
        user.setBusinessId(business.getId());
        user.setUsername(body.ownerUsername());
        user.setEmail(body.ownerEmail());
        user.setFirstName(body.ownerFirstName());
        user.setLastName(body.ownerLastName());
        user.setEnabled(true);
        user.setPrimaryShopId(finalShop.getId());
        user.addShop(finalShop);
        user = userRepository.save(user);

        String tempPassword = UUID.randomUUID().toString().substring(0, 12) + "Aa1!";
        AuthAccount auth = new AuthAccount();
        auth.setUser(user);
        auth.setPasswordHash(passwordEncoder.encode(tempPassword));
        auth.setPasswordLastChanged(LocalDateTime.now());
        auth.setFailedAttempts(0);
        auth.setTotpEnabled(false);
        authAccountRepository.save(auth);

        Role role = roleRepository.findByName("BUSINESS_ADMIN")
                .orElseThrow(() -> new BusinessException("Business admin role is not seeded", "ROLE_NOT_FOUND"));
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setBusinessId(business.getId());
        ur.setShopId(finalShop.getId());
        user.getRoles().add(ur);
        userRepository.save(user);

        log.info("Owner provisioned for business {}: user={}", business.getId(), user.getId());
        return new OwnerProvisioned(user.getId(), user.getUsername(), user.getEmail(), tempPassword);
    }

    // -----------------------------------------------------------------------
    // Erasure — consent-confirmed purge of an archived / cancelled business
    // -----------------------------------------------------------------------

    public record PurgeRequest(String confirm) {}

    @DeleteMapping("/{businessId}/purge")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.purge')")
    public ResponseEntity<Map<String, Object>> purgeBusiness(
            @PathVariable UUID businessId,
            @RequestBody PurgeRequest body) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));

        if (!(business.getStatus() == Business.Status.ARCHIVED
                || business.getStatus() == Business.Status.CANCELLED)) {
            throw new BusinessException(
                    "Only archived or cancelled businesses can be purged (current: " + business.getStatus() + ")",
                    "INVALID_STATE", "status");
        }
        if (!"PURGE".equalsIgnoreCase(body.confirm())) {
            throw new BusinessException("Confirmation text 'PURGE' is required", "CONFIRMATION_REQUIRED", "confirm");
        }

        int rowsDeleted = businessErasureService.erase(businessId);
        log.warn("Business {} purged by platform staff: rowsDeleted={}", businessId, rowsDeleted);
        return ResponseEntity.ok(Map.of(
                "businessId", businessId.toString(),
                "status", "PURGED",
                "rowsDeleted", rowsDeleted));
    }

    // -----------------------------------------------------------------------
    // CSV export of all businesses (respects current filters)
    // -----------------------------------------------------------------------

    @GetMapping("/export")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.view')")
    public ResponseEntity<String> exportBusinesses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String search) {

        var spec = businessSpec(status, plan, search);
        List<Business> all = spec == null
                ? businessRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                : businessRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        StringBuilder csv = new StringBuilder("id,name,slug,status,plan,email,phone,trial_ends_at,created_at\n");
        for (Business b : all) {
            csv.append(csvCell(b.getId())).append(',')
                    .append(csvCell(b.getName())).append(',')
                    .append(csvCell(b.getSlug())).append(',')
                    .append(csvCell(b.getStatus() != null ? b.getStatus().name() : "")).append(',')
                    .append(csvCell(b.getPlan() != null ? b.getPlan().name() : "")).append(',')
                    .append(csvCell(b.getEmail())).append(',')
                    .append(csvCell(b.getPhone())).append(',')
                    .append(csvCell(b.getTrialEndsAt() != null ? b.getTrialEndsAt().toString() : "")).append(',')
                    .append(csvCell(b.getCreatedAt() != null ? b.getCreatedAt().toString() : ""))
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=qliina-businesses.csv")
                .header("Content-Type", "text/csv; charset=utf-8")
                .body(csv.toString());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Optional filter specification — null means "no filters". */
    private org.springframework.data.jpa.domain.Specification<Business> businessSpec(
            String status, String plan, String search) {
        boolean none = (status == null || status.isBlank())
                && (plan == null || plan.isBlank())
                && (search == null || search.isBlank());
        if (none) return null;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                try { predicates.add(cb.equal(root.get("status"), Business.Status.valueOf(status.toUpperCase()))); }
                catch (IllegalArgumentException e) {
                    throw new BusinessException("Invalid status filter: " + status, "INVALID_STATUS", "status");
                }
            }
            if (plan != null && !plan.isBlank()) {
                try { predicates.add(cb.equal(root.get("plan"), Business.Plan.valueOf(plan.toUpperCase()))); }
                catch (IllegalArgumentException e) {
                    throw new BusinessException("Invalid plan filter: " + plan, "INVALID_PLAN", "plan");
                }
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                Predicate name = cb.like(cb.lower(root.get("name")), like);
                Predicate slug = cb.like(cb.lower(root.get("slug")), like);
                predicates.add(cb.or(name, slug));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String csvCell(Object value) {
        if (value == null) return "";
        String s = value.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? '"' + s.replace("\"", "\"\"") + '"'
                : s;
    }

    /** Returns a role-appropriate view object. */
    private Object buildView(Business b, String role) {
        return switch (role) {
            case "BILLING_ADMIN" -> buildBillingView(b);
            case "SUPPORT_AGENT" -> buildSupportView(b);
            default              -> toDTO(b);         // SUPER_ADMIN, PLATFORM_ADMIN, READONLY_AUDITOR
        };
    }

    /** Full DTO for super/platform admins. */
    private BusinessDTO toDTO(Business b) {
        return BusinessDTO.builder()
                .id(b.getId())
                .name(b.getName())
                .slug(b.getSlug())
                .status(b.getStatus())
                .plan(b.getPlan())
                .email(b.getEmail())
                .phone(b.getPhone())
                .logoUrl(b.getLogoUrl())
                .trialEndsAt(b.getTrialEndsAt())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .setupRequired(b.getOnboardingCompletedAt() == null)
                .build();
    }

    /** Billing-only view: name, status, plan, trial. */
    private Map<String, Object> buildBillingView(Business b) {
        return Map.of(
                "id",          b.getId(),
                "name",        b.getName(),
                "status",      b.getStatus(),
                "plan",        b.getPlan(),
                "trialEndsAt", b.getTrialEndsAt() != null ? b.getTrialEndsAt().toString() : ""
        );
    }

    /** Support view: name, status, plan, masked contact info. */
    private Map<String, Object> buildSupportView(Business b) {
        return Map.of(
                "id",     b.getId(),
                "name",   b.getName(),
                "status", b.getStatus(),
                "plan",   b.getPlan(),
                "email",  b.getEmail() != null ? MaskingUtils.maskEmail(b.getEmail()) : "—",
                "phone",  b.getPhone() != null ? MaskingUtils.maskPhone(b.getPhone()) : "—"
        );
    }

    /**
     * Returns the most privileged platform role the caller holds.
     * Looks up the user entity because Spring authorities only contain permissions,
     * not role names, in this application's security model.
     */
    private String dominantPlatformRole(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) return "READONLY_AUDITOR";
        // Priority order: most privileged first
        List<String> ordered = List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "BILLING_ADMIN",
                "SUPPORT_AGENT", "READONLY_AUDITOR");
        return user.getRoles().stream()
                .map(ur -> ur.getRole().getName())
                .filter(ordered::contains)
                .findFirst()
                .orElse("READONLY_AUDITOR");
    }
}
