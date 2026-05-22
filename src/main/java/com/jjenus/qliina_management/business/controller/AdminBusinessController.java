package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.business.dto.BusinessDTO;
import com.jjenus.qliina_management.business.dto.PlanUsageDTO;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.service.PlanLimitService;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.MaskingUtils;
import com.jjenus.qliina_management.common.PageResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

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

    // -----------------------------------------------------------------------
    // List all businesses
    // -----------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ADMIN','SUPPORT_AGENT','BILLING_ADMIN','READONLY_AUDITOR')")
    public ResponseEntity<?> listBusinesses(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication auth) {

        var page = businessRepository.findAll(pageable);
        var role = dominantPlatformRole(auth);

        List<?> items = page.getContent().stream()
                .map(b -> buildView(b, role))
                .toList();

        return ResponseEntity.ok(Map.of(
                "content",       items,
                "totalElements", page.getTotalElements(),
                "totalPages",    page.getTotalPages(),
                "page",          page.getNumber()
        ));
    }

    // -----------------------------------------------------------------------
    // Get single business
    // -----------------------------------------------------------------------

    @GetMapping("/{businessId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ADMIN','SUPPORT_AGENT','BILLING_ADMIN','READONLY_AUDITOR')")
    public ResponseEntity<?> getBusiness(
            @PathVariable UUID businessId,
            Authentication auth) {
        Business b = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        return ResponseEntity.ok(buildView(b, dominantPlatformRole(auth)));
    }

    // -----------------------------------------------------------------------
    // Change plan tier
    // -----------------------------------------------------------------------

    @PatchMapping("/{businessId}/plan")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ADMIN','BILLING_ADMIN')")
    public ResponseEntity<BusinessDTO> changePlan(
            @PathVariable UUID businessId,
            @RequestBody Map<String, String> body) {
        String tier = body.get("plan");
        if (tier == null) throw new BusinessException("'plan' field is required", "VALIDATION_ERROR", "plan");
        Business.Plan plan;
        try { plan = Business.Plan.valueOf(tier.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid plan tier: " + tier, "INVALID_PLAN", "plan");
        }
        Business b = businessRepository.findById(businessId)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
        b.setPlan(plan);
        businessRepository.save(b);
        log.info("Plan changed: businessId={}, newPlan={}", businessId, plan);
        return ResponseEntity.ok(toDTO(b));
    }

    // -----------------------------------------------------------------------
    // Change status (suspend / activate / cancel)
    // -----------------------------------------------------------------------

    @PatchMapping("/{businessId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ADMIN')")
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
        b.setStatus(status);
        businessRepository.save(b);
        log.info("Status changed: businessId={}, newStatus={}", businessId, status);
        return ResponseEntity.ok(toDTO(b));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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

    /** Returns the most privileged platform role the caller holds. */
    private String dominantPlatformRole(Authentication auth) {
        List<String> platformRoles = List.of("SUPER_ADMIN", "PLATFORM_ADMIN", "BILLING_ADMIN",
                "SUPPORT_AGENT", "READONLY_AUDITOR");
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .filter(platformRoles::contains)
                .findFirst()
                .orElse("READONLY_AUDITOR");
    }
}
