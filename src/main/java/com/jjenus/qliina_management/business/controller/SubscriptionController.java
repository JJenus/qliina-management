package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.billing.dto.PlanDTO;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.service.BillingQueryService;
import com.jjenus.qliina_management.business.dto.BusinessDTO;
import com.jjenus.qliina_management.business.dto.PlanUsageDTO;
import com.jjenus.qliina_management.business.service.BusinessService;
import com.jjenus.qliina_management.business.service.PlanLimitService;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription endpoints — all backed by the billing schema (MD §2):
 *   GET  /api/v1/subscription/plans           — public list of active billing plans
 *   GET  /api/v1/{businessId}/subscription    — usage summary for authenticated business
 *   POST /api/v1/{businessId}/subscription/change-plan — self-service plan change
 */
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final BillingQueryService queryService;
    private final PlanLimitService    planLimitService;
    private final BusinessService     businessService;

    // -------------------------------------------------------------------------
    // Public: list active plans (for signup page, settings comparison table)
    // -------------------------------------------------------------------------

    @GetMapping("/api/v1/subscription/plans")
    public List<PlanDTO> listActivePlans() {
        return queryService.plans().stream()
                .filter(p -> p.status() == PlanStatus.ACTIVE)
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
    // Self-service: change plan (business owner)
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/{businessId}/subscription/change-plan
     * Body: { "plan": "STARTER" | "PRO" | "FREE" }
     *
     * Accessible by business owners/admins (admin.settings permission).
     * Delegates to the billing engine (MD §7): upgrades are prorated and
     * charged immediately when a payment method is on file; downgrades are
     * deferred to the next renewal.
     */
    @PostMapping("/api/v1/{businessId}/subscription/change-plan")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<BusinessDTO> changePlan(
            @PathVariable UUID businessId,
            @RequestBody Map<String, String> body) {
        String plan = body.get("plan");
        if (plan == null || plan.isBlank()) {
            throw new BusinessException("'plan' field is required", "MISSING_PLAN", "plan");
        }
        return ResponseEntity.ok(businessService.changePlan(businessId, plan));
    }
}
