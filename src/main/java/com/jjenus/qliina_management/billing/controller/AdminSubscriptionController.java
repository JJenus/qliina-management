package com.jjenus.qliina_management.billing.controller;

import com.jjenus.qliina_management.billing.model.BillingInvoice;
import com.jjenus.qliina_management.billing.model.BillingPayment;
import com.jjenus.qliina_management.billing.model.InvoiceStatus;
import com.jjenus.qliina_management.billing.model.Subscription;
import com.jjenus.qliina_management.billing.model.SubscriptionStatus;
import com.jjenus.qliina_management.billing.model.UsageRecord;
import com.jjenus.qliina_management.billing.repository.BillingInvoiceRepository;
import com.jjenus.qliina_management.billing.repository.BillingPaymentRepository;
import com.jjenus.qliina_management.billing.repository.InvoiceLineItemRepository;
import com.jjenus.qliina_management.billing.repository.SubscriptionRepository;
import com.jjenus.qliina_management.billing.repository.UsageRecordRepository;
import com.jjenus.qliina_management.billing.service.DunningService;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platform-admin cross-tenant billing listings:
 *   GET  /api/v1/admin/subscriptions            — all subscriptions (filter by status)
 *   GET  /api/v1/admin/invoices                 — all invoices (filter by status)
 *   GET  /api/v1/admin/invoices/{id}            — invoice detail + line items + payments
 *   POST /api/v1/admin/invoices/{id}/retry      — re-open a failed invoice & trigger dunning retry
 *   GET  /api/v1/admin/usage/{businessId}       — metered usage records vs plan limits
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private static final String BILLING = "hasPermission(null, 'PLATFORM', 'platform.billing.manage')";

    private final SubscriptionRepository subscriptionRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final BillingPaymentRepository paymentRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final BusinessRepository businessRepository;
    private final DunningService dunningService;

    // ---------------------------------------------------------------------
    // Subscriptions
    // ---------------------------------------------------------------------

    @GetMapping("/subscriptions")
    @PreAuthorize(BILLING)
    public PageResponse<AdminSubscriptionRow> listSubscriptions(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        // Tenant-scoped listing (business detail page): resolve that tenant's
        // subscriptions first, then page in memory — a tenant has few rows.
        if (businessId != null) {
            List<Subscription> scoped = subscriptionRepository.findByBusinessId(businessId).stream()
                    .filter(s -> status == null || status.isBlank()
                            || s.getStatus() == parseStatus(status))
                    .toList();
            int start = (int) pageable.getOffset();
            List<Subscription> slice = start >= scoped.size() ? List.of()
                    : scoped.subList(start, Math.min(start + pageable.getPageSize(), scoped.size()));
            Map<UUID, String> scopedNames = businessNames(slice.stream().map(Subscription::getBusinessId).toList());
            return PageResponse.from(new PageImpl<>(slice, pageable, scoped.size())
                    .map(s -> toRow(s, scopedNames.get(s.getBusinessId()))));
        }

        Page<Subscription> page;
        if (status != null && !status.isBlank()) {
            SubscriptionStatus st = parseStatus(status);
            page = subscriptionRepository.findByStatus(st, pageable);
        } else {
            page = subscriptionRepository.findAll(pageable);
        }

        Map<UUID, String> names = businessNames(page.getContent().stream()
                .map(Subscription::getBusinessId).toList());

        return PageResponse.from(page.map(s -> toRow(s, names.get(s.getBusinessId()))));
    }

    @Builder
    public record AdminSubscriptionRow(
            UUID id, UUID businessId, String businessName, String planName,
            BigDecimal price, String currency, String status,
            LocalDateTime currentPeriodStart, LocalDateTime currentPeriodEnd,
            LocalDateTime trialEndsAt, boolean cancelAtPeriodEnd,
            int retryCount, LocalDateTime nextRetryAt,
            String pendingPlanName, LocalDateTime pendingChangeAt
    ) {}

    private AdminSubscriptionRow toRow(Subscription s, String businessName) {
        return AdminSubscriptionRow.builder()
                .id(s.getId())
                .businessId(s.getBusinessId())
                .businessName(businessName)
                .planName(s.getPlan() != null ? s.getPlan().getName() : null)
                .price(s.getPlanVersion() != null ? s.getPlanVersion().getPrice() : null)
                .currency(s.getPlanVersion() != null ? s.getPlanVersion().getCurrency() : null)
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .currentPeriodStart(s.getCurrentPeriodStart())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .trialEndsAt(s.getTrialEndsAt())
                .cancelAtPeriodEnd(s.isCancelAtPeriodEnd())
                .retryCount(s.getRetryCount())
                .nextRetryAt(s.getNextRetryAt())
                .pendingPlanName(s.getPendingPlan() != null ? s.getPendingPlan().getName() : null)
                .pendingChangeAt(s.getPendingChangeAt())
                .build();
    }

    // ---------------------------------------------------------------------
    // Invoices
    // ---------------------------------------------------------------------

    @GetMapping("/invoices")
    @PreAuthorize(BILLING)
    public PageResponse<AdminInvoiceRow> listInvoices(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "issuedAt") Pageable pageable) {

        Sort effective = pageable.getSort().isUnsorted()
                ? Sort.by(Sort.Direction.DESC, "issuedAt") : pageable.getSort();
        Pageable p = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);

        InvoiceStatus st = (status != null && !status.isBlank()) ? parseInvoiceStatus(status) : null;

        Page<BillingInvoice> page;
        if (businessId != null) {
            List<UUID> subIds = subscriptionRepository.findByBusinessId(businessId).stream()
                    .map(Subscription::getId).toList();
            page = subIds.isEmpty()
                    ? new PageImpl<>(List.of(), p, 0)
                    : (st != null
                        ? invoiceRepository.findBySubscriptionIdInAndStatus(subIds, st, p)
                        : invoiceRepository.findBySubscriptionIdIn(subIds, p));
        } else {
            page = (st != null)
                    ? invoiceRepository.findByStatus(st, p)
                    : invoiceRepository.findAll(p);
        }

        Map<UUID, String> subBiz = resolveInvoiceBusinesses(page.getContent());
        return PageResponse.from(page.map(inv -> toInvoiceRow(inv, subBiz.get(inv.getSubscriptionId()))));
    }

    @Builder
    public record AdminInvoiceRow(
            UUID id, String invoiceNumber, BigDecimal amount, String status,
            LocalDateTime issuedAt, LocalDateTime dueAt,
            UUID subscriptionId, UUID businessId, String businessName
    ) {}

    private AdminInvoiceRow toInvoiceRow(BillingInvoice inv, String businessName) {
        return AdminInvoiceRow.builder()
                .id(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .amount(inv.getAmount())
                .status(inv.getStatus() != null ? inv.getStatus().name() : null)
                .issuedAt(inv.getIssuedAt())
                .dueAt(inv.getDueAt())
                .subscriptionId(inv.getSubscriptionId())
                .businessId(subscriptionBusinessId(inv.getSubscriptionId()))
                .businessName(businessName)
                .build();
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize(BILLING)
    public ResponseEntity<AdminInvoiceDetail> getInvoice(@PathVariable UUID id) {
        BillingInvoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Invoice not found", "INVOICE_NOT_FOUND"));

        List<AdminLineItem> lineItems = lineItemRepository.findAllByInvoiceIdOrderByCreatedAtAsc(id).stream()
                .map(li -> AdminLineItem.builder()
                        .id(li.getId())
                        .type(li.getType() != null ? li.getType().name() : null)
                        .description(li.getDescription())
                        .amount(li.getAmount())
                        .periodStart(li.getPeriodStart())
                        .periodEnd(li.getPeriodEnd())
                        .build())
                .toList();

        List<AdminPayment> payments = paymentRepository.findAllByInvoiceId(id).stream()
                .map(p -> AdminPayment.builder()
                        .id(p.getId())
                        .gateway(p.getGateway())
                        .gatewayTransactionId(p.getGatewayTransactionId())
                        .amount(p.getAmount())
                        .feeAmount(p.getFeeAmount())
                        .netAmount(p.getNetAmount())
                        .status(p.getStatus() != null ? p.getStatus().name() : null)
                        .paidAt(p.getPaidAt())
                        .build())
                .toList();

        return ResponseEntity.ok(AdminInvoiceDetail.builder()
                .id(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .amount(inv.getAmount())
                .status(inv.getStatus() != null ? inv.getStatus().name() : null)
                .issuedAt(inv.getIssuedAt())
                .dueAt(inv.getDueAt())
                .subscriptionId(inv.getSubscriptionId())
                .businessId(subscriptionBusinessId(inv.getSubscriptionId()))
                .lineItems(lineItems)
                .payments(payments)
                .build());
    }

    @Builder
    public record AdminInvoiceDetail(
            UUID id, String invoiceNumber, BigDecimal amount, String status,
            LocalDateTime issuedAt, LocalDateTime dueAt,
            UUID subscriptionId, UUID businessId,
            List<AdminLineItem> lineItems, List<AdminPayment> payments
    ) {}

    @Builder
    public record AdminLineItem(UUID id, String type, String description, BigDecimal amount,
                                LocalDateTime periodStart, LocalDateTime periodEnd) {}

    @Builder
    public record AdminPayment(UUID id, String gateway, String gatewayTransactionId,
                               BigDecimal amount, BigDecimal feeAmount, BigDecimal netAmount,
                               String status, LocalDateTime paidAt) {}

    /**
     * Manual retry: a FAILED invoice is re-opened, then the dunning retry path
     * runs for the owning subscription (charge attempt + schedule update).
     */
    @PostMapping("/invoices/{id}/retry")
    @PreAuthorize(BILLING)
    public ResponseEntity<Map<String, Object>> retryInvoice(@PathVariable UUID id) {
        BillingInvoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Invoice not found", "INVOICE_NOT_FOUND"));

        if (inv.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessException("Invoice is already paid", "INVOICE_ALREADY_PAID");
        }
        if (inv.getStatus() == InvoiceStatus.FAILED) {
            inv.setStatus(InvoiceStatus.OPEN);
            invoiceRepository.save(inv);
        }

        UUID subscriptionId = inv.getSubscriptionId();
        dunningService.retry(subscriptionId);

        BillingInvoice after = invoiceRepository.findById(id).orElse(inv);
        return ResponseEntity.ok(Map.of(
                "invoiceId", id,
                "status", after.getStatus() != null ? after.getStatus().name() : "UNKNOWN",
                "retriedAt", LocalDateTime.now().toString()
        ));
    }

    // ---------------------------------------------------------------------
    // Metered usage viewer
    // ---------------------------------------------------------------------

    @GetMapping("/usage/{businessId}")
    @PreAuthorize(BILLING +
            " or hasPermission(null, 'PLATFORM', 'platform.support.view')")
    public ResponseEntity<Map<String, Object>> usageRecords(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20) Pageable pageable) {

        var live = subscriptionRepository.findByBusinessIdAndStatusNot(businessId, SubscriptionStatus.CANCELED);
        if (live.isEmpty()) {
            return ResponseEntity.ok(Map.of("subscriptionId", "", "content", List.of(), "totalElements", 0));
        }
        Subscription sub = live.get();

        Page<UsageRecord> page = usageRecordRepository.findBySubscriptionIdOrderByRecordedAtDesc(
                sub.getId(), pageable);

        List<Map<String, Object>> content = page.getContent().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "featureKey", u.getFeatureKey(),
                        "quantity", u.getQuantity(),
                        "recordedAt", u.getRecordedAt()))
                .toList();

        return ResponseEntity.ok(Map.of(
                "subscriptionId", sub.getId(),
                "content", content,
                "pageNumber", page.getNumber(),
                "pageSize", page.getSize(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "last", page.isLast()
        ));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private SubscriptionStatus parseStatus(String raw) {
        try { return SubscriptionStatus.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid subscription status: " + raw, "INVALID_STATUS", "status");
        }
    }

    private InvoiceStatus parseInvoiceStatus(String raw) {
        try { return InvoiceStatus.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid invoice status: " + raw, "INVALID_STATUS", "status");
        }
    }

    private Map<UUID, String> businessNames(List<UUID> businessIds) {
        return businessRepository.findAllById(businessIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(b -> b.getId(), b -> b.getName(), (a, b) -> a));
    }

    /** invoice.subscriptionId → subscription.businessId lookup cache. */
    private Map<UUID, String> resolveInvoiceBusinesses(List<BillingInvoice> invoices) {
        List<UUID> subIds = invoices.stream().map(BillingInvoice::getSubscriptionId).distinct().toList();
        Map<UUID, UUID> subToBiz = subscriptionRepository.findAllById(subIds).stream()
                .collect(Collectors.toMap(Subscription::getId, Subscription::getBusinessId, (a, b) -> a));
        Map<UUID, String> bizNames = businessNames(List.copyOf(subToBiz.values()));
        return subIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(),
                        sid -> bizNames.getOrDefault(subToBiz.get(sid), ""), (a, b) -> a));
    }

    private UUID subscriptionBusinessId(UUID subscriptionId) {
        if (subscriptionId == null) return null;
        return subscriptionRepository.findById(subscriptionId)
                .map(Subscription::getBusinessId).orElse(null);
    }
}
