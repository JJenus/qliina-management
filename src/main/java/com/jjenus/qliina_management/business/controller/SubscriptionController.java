package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.business.dto.PlanUsageDTO;
import com.jjenus.qliina_management.business.dto.SubscriptionPlanDTO;
import com.jjenus.qliina_management.business.model.SubscriptionPlan;
import com.jjenus.qliina_management.business.repository.SubscriptionPlanRepository;
import com.jjenus.qliina_management.business.service.PlanLimitService;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Subscription plan endpoints:
 *   GET  /api/v1/subscription/plans           — public list of active plans
 *   GET  /api/v1/{businessId}/subscription    — usage summary for authenticated business
 *   PUT  /api/v1/admin/subscription/plans/{id}— update plan (SUPER_ADMIN / PLATFORM_ADMIN)
 *   POST /api/v1/admin/subscription/plans     — create plan (SUPER_ADMIN / PLATFORM_ADMIN)
 */
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPlanRepository planRepository;
    private final PlanLimitService           planLimitService;

    // -------------------------------------------------------------------------
    // Public: list active plans (for signup page, settings comparison table)
    // -------------------------------------------------------------------------

    @GetMapping("/api/v1/subscription/plans")
    public List<SubscriptionPlanDTO> listActivePlans() {
        return planRepository.findAllByIsActiveTrue().stream()
                .map(this::toDTO)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Authenticated: current business usage summary
    // -------------------------------------------------------------------------

    /**
     * Usage summary accessible by:
     *   - Business owners/admins (business-scoped admin.settings)
     *   - Platform staff with billing or audit view access
     *     (SUPER_ADMIN, PLATFORM_ADMIN, BILLING_ADMIN, READONLY_AUDITOR)
     *   SUPPORT_AGENT is intentionally excluded (no financial/billing data).
     */
    @GetMapping("/api/v1/{businessId}/subscription")
    @PreAuthorize("""
        hasPermission(#businessId, 'BUSINESS', 'admin.settings')
        or hasPermission(null, 'PLATFORM', 'platform.billing.manage')
        or hasPermission(null, 'PLATFORM', 'platform.plans.manage')
        or hasPermission(null, 'PLATFORM', 'platform.audit.view')
    """)
    public ResponseEntity<PlanUsageDTO> getUsage(@PathVariable UUID businessId) {
        return ResponseEntity.ok(planLimitService.getUsageSummary(businessId));
    }

    // -------------------------------------------------------------------------
    // Admin: create / update plans
    // -------------------------------------------------------------------------

    @PostMapping("/api/v1/admin/subscription/plans")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.plans.manage')")
    public ResponseEntity<SubscriptionPlanDTO> createPlan(@RequestBody SubscriptionPlanDTO dto) {
        if (planRepository.existsByTier(dto.getTier())) {
            throw new BusinessException("A plan with tier '" + dto.getTier() + "' already exists", "PLAN_EXISTS", "tier");
        }
        SubscriptionPlan plan = fromDTO(dto);
        return ResponseEntity.ok(toDTO(planRepository.save(plan)));
    }

    @PutMapping("/api/v1/admin/subscription/plans/{id}")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.plans.manage')")
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(
            @PathVariable UUID id,
            @RequestBody SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Plan not found", "PLAN_NOT_FOUND"));
        applyDTO(plan, dto);
        return ResponseEntity.ok(toDTO(planRepository.save(plan)));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private SubscriptionPlanDTO toDTO(SubscriptionPlan p) {
        return SubscriptionPlanDTO.builder()
                .id(p.getId())
                .tier(p.getTier())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .maxShops(p.getMaxShops())
                .maxUsers(p.getMaxUsers())
                .maxEmployees(p.getMaxEmployees())
                .maxOrdersPerMonth(p.getMaxOrdersPerMonth())
                .maxServiceCatalogItems(p.getMaxServiceCatalogItems())
                .maxInventoryItems(p.getMaxInventoryItems())
                .dataRetentionDays(p.getDataRetentionDays())
                .advancedAnalytics(p.isAdvancedAnalytics())
                .exportReports(p.isExportReports())
                .loyaltyProgram(p.isLoyaltyProgram())
                .qualityControl(p.isQualityControl())
                .apiAccess(p.isApiAccess())
                .prioritySupport(p.isPrioritySupport())
                .customBranding(p.isCustomBranding())
                .multiShopReporting(p.isMultiShopReporting())
                .isActive(p.isActive())
                .build();
    }

    private SubscriptionPlan fromDTO(SubscriptionPlanDTO dto) {
        SubscriptionPlan p = new SubscriptionPlan();
        applyDTO(p, dto);
        return p;
    }

    private void applyDTO(SubscriptionPlan p, SubscriptionPlanDTO dto) {
        if (dto.getTier()        != null) p.setTier(dto.getTier());
        if (dto.getName()        != null) p.setName(dto.getName());
        if (dto.getDescription() != null) p.setDescription(dto.getDescription());
        if (dto.getPrice()       != null) p.setPrice(dto.getPrice());
        if (dto.getMaxShops()    != 0)    p.setMaxShops(dto.getMaxShops());
        if (dto.getMaxUsers()    != 0)    p.setMaxUsers(dto.getMaxUsers());
        if (dto.getMaxEmployees()         != 0) p.setMaxEmployees(dto.getMaxEmployees());
        if (dto.getMaxOrdersPerMonth()    != 0) p.setMaxOrdersPerMonth(dto.getMaxOrdersPerMonth());
        if (dto.getMaxServiceCatalogItems() != 0) p.setMaxServiceCatalogItems(dto.getMaxServiceCatalogItems());
        if (dto.getMaxInventoryItems()    != 0) p.setMaxInventoryItems(dto.getMaxInventoryItems());
        if (dto.getDataRetentionDays()    != 0) p.setDataRetentionDays(dto.getDataRetentionDays());
        p.setAdvancedAnalytics(dto.isAdvancedAnalytics());
        p.setExportReports(dto.isExportReports());
        p.setLoyaltyProgram(dto.isLoyaltyProgram());
        p.setQualityControl(dto.isQualityControl());
        p.setApiAccess(dto.isApiAccess());
        p.setPrioritySupport(dto.isPrioritySupport());
        p.setCustomBranding(dto.isCustomBranding());
        p.setMultiShopReporting(dto.isMultiShopReporting());
        p.setActive(dto.isActive());
    }
}
