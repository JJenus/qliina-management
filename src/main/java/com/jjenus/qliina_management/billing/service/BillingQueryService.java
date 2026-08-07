package com.jjenus.qliina_management.billing.service;

import com.jjenus.qliina_management.billing.dto.*;
import com.jjenus.qliina_management.billing.model.*;
import com.jjenus.qliina_management.billing.repository.*;
import com.jjenus.qliina_management.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-side assembly for the billing API.
 */
@Service
@RequiredArgsConstructor
public class BillingQueryService {

    private final SubscriptionService subscriptionService;
    private final BillingPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final SubscriptionCouponRepository subscriptionCouponRepository;
    private final CouponRepository couponRepository;
    private final BillingPaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public BillingSubscriptionDTO subscription(UUID businessId) {
        Subscription sub = subscriptionService.getLiveSubscription(businessId);
        return subscription(sub);
    }

    /**
     * Builds the read-model DTO from a specific subscription row — used by
     * cancel/reactivate so the response reflects the just-transitioned state
     * even when the subscription is now terminal (no live row remains).
     */
    @Transactional(readOnly = true)
    public BillingSubscriptionDTO subscription(Subscription sub) {
        return new BillingSubscriptionDTO(
                sub.getId(),
                sub.getBusinessId(),
                sub.getPlan().getId(),
                sub.getPlan().getName(),
                sub.getStatus(),
                sub.getPlanVersion().getPrice(),
                sub.getPlanVersion().getCurrency(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getTrialEndsAt(),
                sub.isCancelAtPeriodEnd(),
                sub.getRetryCount(),
                sub.getNextRetryAt(),
                sub.getPendingPlan() != null ? sub.getPendingPlan().getName() : null,
                sub.getPendingChangeAt(),
                appliedCouponCode(sub.getId()));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceDTO> invoices(UUID businessId, Pageable pageable) {
        Subscription sub = subscriptionService.getLiveSubscription(businessId);
        return invoiceRepository.findAllBySubscriptionIdOrderByIssuedAtDesc(sub.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public InvoiceDTO invoice(UUID businessId, UUID invoiceId) {
        Subscription sub = subscriptionService.getLiveSubscription(businessId);
        BillingInvoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> i.getSubscriptionId().equals(sub.getId()))
                .orElseThrow(() -> new BusinessException("Invoice not found", "INVOICE_NOT_FOUND"));
        return toDTO(invoice);
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> paymentsForInvoice(UUID invoiceId) {
        return paymentRepository.findAllByInvoiceId(invoiceId).stream()
                .map(p -> new PaymentDTO(p.getId(), p.getInvoiceId(), p.getGateway(),
                        p.getGatewayTransactionId(), p.getAmount(), p.getFeeAmount(),
                        p.getNetAmount(), p.getStatus(), p.getPaidAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDTO plan(UUID planId) {
        BillingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Plan not found", "PLAN_NOT_FOUND"));
        List<PlanVersionDTO> versions = versionRepository.findAllByPlanIdOrderByEffectiveFromAsc(planId).stream()
                .map(v -> new PlanVersionDTO(v.getId(), v.getPrice(), v.getCurrency(), v.getEffectiveFrom()))
                .toList();
        List<PlanFeatureDTO> features = planFeatureRepository.findAllByPlanId(planId).stream()
                .map(f -> new PlanFeatureDTO(f.getFeatureKey(), f.getValue(), f.isHardLimit()))
                .toList();
        return new PlanDTO(plan.getId(), plan.getName(), plan.getDescription(),
                plan.getStatus(), plan.getArchivedAt(), versions, features);
    }

    @Transactional(readOnly = true)
    public List<PlanDTO> plans() {
        return planRepository.findAll().stream().map(p -> plan(p.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<CouponDTO> coupons() {
        return couponRepository.findAll().stream()
                .map(c -> new CouponDTO(c.getId(), c.getCode(), c.getDiscountType(), c.getDiscountValue(),
                        c.getMaxRedemptions(), c.getRedemptionsCount(), c.getMaxRedemptionsPerBusiness(),
                        c.getStartsAt(), c.getExpiresAt(), c.getStatus()))
                .toList();
    }

    private InvoiceDTO toDTO(BillingInvoice invoice) {
        List<InvoiceLineItemDTO> items = lineItemRepository.findAllByInvoiceIdOrderByCreatedAtAsc(invoice.getId())
                .stream()
                .map(li -> new InvoiceLineItemDTO(li.getType(), li.getDescription(), li.getAmount(),
                        li.getPeriodStart(), li.getPeriodEnd()))
                .toList();
        return new InvoiceDTO(invoice.getId(), invoice.getSubscriptionId(), invoice.getInvoiceNumber(),
                invoice.getAmount(), invoice.getStatus(), invoice.getIssuedAt(), invoice.getDueAt(), items);
    }

    private String appliedCouponCode(UUID subscriptionId) {
        return subscriptionCouponRepository.findAll().stream()
                .filter(sc -> sc.getId().getSubscriptionId().equals(subscriptionId))
                .map(sc -> sc.getCoupon().getCode())
                .findFirst()
                .orElse(null);
    }
}
