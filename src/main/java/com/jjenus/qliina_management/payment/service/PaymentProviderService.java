package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.websocket.WebSocketPublisher;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.model.OrderTimeline;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.payment.dto.GeneratePaymentRequest;
import com.jjenus.qliina_management.payment.dto.GeneratePaymentResultDTO;
import com.jjenus.qliina_management.payment.dto.PaymentProviderDTO;
import com.jjenus.qliina_management.payment.dto.PaymentVerifyDTO;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.provider.PaymentProvider;
import com.jjenus.qliina_management.payment.provider.PaymentProviderRegistry;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the online (provider-backed) segment of the POS checkout:
 * provider availability, charge initiation, hosted-checkout generation
 * (payment link/QR), endpoint verification, webhook reconciliation and
 * provider refunds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderService {

    private final PaymentProviderRegistry registry;
    private final PaymentProviderConfigService configService;
    private final OrderPaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PaymentReconciliationService reconciliationService;
    private final WebSocketPublisher webSocketPublisher;

    @Value("${app.payments.currency:NGN}")
    private String currency;

    @Value("${app.payments.pending-expiry-hours:24}")
    private long pendingExpiryHours;

    /** A provider plus the business's resolved connection context (ready to charge). */
    private record Connectable(PaymentProvider provider, PaymentProvider.Connection connection) {
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<PaymentProviderDTO> listProviders(UUID businessId) {
        return configService.list(businessId);
    }

    @Transactional
    public PaymentProviderDTO setProviderEnabled(UUID businessId, String providerName, boolean enabled) {
        configService.setEnabled(businessId, providerName, enabled);
        return configService.list(businessId).stream()
                .filter(dto -> dto.getName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unknown payment provider: " + providerName,
                        "PROVIDER_UNKNOWN", "provider"));
    }

    @Transactional
    public PaymentProviderDTO setProviderConnection(UUID businessId, String providerName,
            com.jjenus.qliina_management.payment.dto.UpdateProviderConnectionRequest request) {
        configService.setProviderConnection(businessId, providerName, request);
        return configService.list(businessId).stream()
                .filter(dto -> dto.getName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unknown payment provider: " + providerName,
                        "PROVIDER_UNKNOWN", "provider"));
    }

    // ----------------------------------------------------------- checkout

    /**
     * Requires an enabled and connectable provider, then initiates the charge.
     * Fail-closed: disabled/unknown/unconfigured providers never touch the
     * downstream processor. The business's connection model (platform subaccount
     * or decrypted BYO credentials) rides on the charge request.
     */
    public PaymentProvider.ChargeResult chargeOnline(UUID businessId, String providerName,
            BigDecimal amount, String customerEmail, String customerName, String reference) {
        Connectable connectable = requireConnectable(businessId, providerName);
        return connectable.provider().charge(new PaymentProvider.ChargeRequest(
                amount, currency, customerEmail, customerName, reference, null, connectable.connection()));
    }

    private Connectable requireConnectable(UUID businessId, String providerName) {
        PaymentProvider provider = registry.require(providerName);
        String name = provider.getName();
        if (!configService.isEnabled(businessId, name)) {
            throw new BusinessException("Payment provider is disabled for this business", "PROVIDER_DISABLED",
                    "provider");
        }
        PaymentProvider.Connection connection = configService.resolveConnection(businessId, provider);
        boolean connectable = switch (connection.mode()) {
            case "PLATFORM" -> provider.isConfigured();
            case "BYO" -> connection.credentials() != null;
            default -> false;
        };
        if (!connectable) {
            throw new BusinessException("Payment provider is not configured", "PROVIDER_NOT_CONFIGURED",
                    "provider");
        }
        return new Connectable(provider, connection);
    }

    /**
     * Starts a hosted checkout for (part of) an order's balance through a
     * connectable provider. The result is persisted as a PENDING payment whose
     * authorization URL the business can show as a link or QR code; settlement
     * arrives via the provider webhook or a manual recheck.
     */
    @Transactional
    public GeneratePaymentResultDTO generatePaymentLink(UUID businessId, UUID orderId,
            GeneratePaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        if (!order.getBusinessId().equals(businessId)) {
            throw new BusinessException("Order not found", "ORDER_NOT_FOUND");
        }
        Connectable connectable = requireConnectable(businessId, request.getProvider());
        PaymentProvider provider = connectable.provider();

        // Idempotency: if there is already an active (still PENDING) authorization for
        // this order + provider, return it rather than stacking a duplicate charge.
        List<OrderPayment> existing = paymentRepository.findPendingByOrderAndProvider(orderId, provider.getName());
        if (!existing.isEmpty()) {
            OrderPayment active = existing.get(0);
            String checkoutUrl = active.getMetadata() != null
                    ? (String) active.getMetadata().get("checkoutUrl") : null;
            BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal settled = paymentRepository.sumCompletedPaymentsByOrderId(orderId);
            BigDecimal balanceDue = settled.compareTo(orderTotal) >= 0 ? BigDecimal.ZERO : orderTotal.subtract(settled);
            return GeneratePaymentResultDTO.builder()
                    .paymentId(active.getId())
                    .orderId(orderId)
                    .orderNumber(order.getOrderNumber())
                    .amount(active.getAmount())
                    .method(active.getMethod())
                    .reference(active.getReference())
                    .provider(active.getProvider())
                    .providerReference(active.getProviderReference())
                    .status("PENDING")
                    .checkoutUrl(checkoutUrl)
                    .qrPayload(checkoutUrl)
                    .balanceDue(balanceDue)
                    .isFullyPaid(false)
                    .build();
        }

        String method = request.getMethod() != null ? request.getMethod().trim()
                : provider.supportedMethods().stream().findFirst().orElse("CARD");
        if (!provider.supportedMethods().contains(method)) {
            throw new BusinessException("Provider does not support payment method " + method,
                    "INVALID_PAYMENT_METHOD", "method");
        }

        BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal settled = paymentRepository.sumCompletedPaymentsByOrderId(orderId);
        BigDecimal balanceDue = settled.compareTo(orderTotal) >= 0 ? BigDecimal.ZERO : orderTotal.subtract(settled);

        BigDecimal amount = request.getAmount() != null ? BigDecimal.valueOf(request.getAmount()) : balanceDue;
        if (balanceDue.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("This order is already fully paid", "ORDER_ALREADY_PAID");
        }
        if (amount.compareTo(balanceDue) > 0) {
            throw new BusinessException("Amount exceeds the order balance due", "PAYMENT_AMOUNT_EXCEEDS_DUE",
                    "amount");
        }

        String reference = "ql_" + UUID.randomUUID();
        Customer customer = order.getCustomerId() != null
                ? customerRepository.findById(order.getCustomerId()).orElse(null) : null;
        String customerEmail = customer != null ? customer.getEmail() : null;
        String customerName = customer != null
                ? (customer.getFirstName() + " " + (customer.getLastName() != null
                        ? customer.getLastName() : "")).trim()
                : null;

        PaymentProvider.ChargeResult result = provider.initiateCheckout(new PaymentProvider.ChargeRequest(
                amount, currency, customerEmail, customerName, reference,
                "Order " + (order.getOrderNumber() != null ? order.getOrderNumber() : ""),
                connectable.connection()));
        if (!StringUtils.hasText(result.checkoutUrl())) {
            throw new BusinessException("Provider could not start a checkout: " + result.message(),
                    "PROVIDER_CHECKOUT_FAILED", "provider");
        }

        OrderPayment payment = new OrderPayment();
        payment.setBusinessId(businessId);
        payment.setShopId(order.getShopId());
        payment.setOrderId(orderId);
        payment.setCustomerId(order.getCustomerId());
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setReference(reference);
        payment.setProvider(provider.getName());
        payment.setProviderReference(result.providerReference());
        payment.setProviderStatus("PENDING");
        payment.setStatus("PENDING");
        payment.setPaidAt(LocalDateTime.now());
        payment.setCollectedBy(getCurrentUserId());
        payment.setMetadata(Map.of("checkoutUrl", result.checkoutUrl()));
        payment = paymentRepository.save(payment);

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("PAYMENT");
        timeline.setDescription(String.format("Payment request of %s %s initiated for authorization (checkout link)",
                payment.getAmount(), payment.getMethod()));
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(payment.getCollectedBy());
        timeline.setUserName(getUserName(payment.getCollectedBy()));
        order.getTimeline().add(timeline);
        orderRepository.save(order);

        publishPaymentEvent(
                businessId, payment.getId(), payment.getOrderId(), "PENDING", payment.getProvider(),
                payment.getProviderReference(), payment.getAmount(), "Payment request initiated for authorization");

        String checkoutUrl = result.checkoutUrl();
        return GeneratePaymentResultDTO.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .orderNumber(order.getOrderNumber())
                .amount(amount)
                .method(method)
                .reference(reference)
                .provider(payment.getProvider())
                .providerReference(payment.getProviderReference())
                .status("PENDING")
                .checkoutUrl(checkoutUrl)
                .qrPayload(checkoutUrl)
                .balanceDue(balanceDue)
                .isFullyPaid(false)
                .build();
    }

    // ---------------------------------------------------------- settlement

    /** Confirms a pending charge and, when settled, completes the payment. */
    @Transactional
    public PaymentVerifyDTO verifyPayment(UUID businessId, UUID paymentId) {
        OrderPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Payment not found", "PAYMENT_NOT_FOUND"));
        if (!payment.getBusinessId().equals(businessId)) {
            throw new BusinessException("Payment not found for this business", "PAYMENT_NOT_FOUND");
        }
        if (!StringUtils.hasText(payment.getProvider()) || !StringUtils.hasText(payment.getProviderReference())) {
            throw new BusinessException("Payment was not processed through an online provider",
                    "NOT_PROVIDER_PAYMENT");
        }

        PaymentProvider provider = registry.require(payment.getProvider());
        PaymentProvider.Connection connection = configService.resolveConnection(businessId, provider);
        PaymentProvider.VerifyResult result = provider.verify(payment.getProviderReference(), connection);

        boolean nowSettled = result.paid() && !"COMPLETED".equals(payment.getStatus());
        if (nowSettled) {
            settlePayment(payment, result.status());
        } else if (StringUtils.hasText(result.status()) && !result.status().equals(payment.getProviderStatus())) {
            payment.setProviderStatus(result.status());
            paymentRepository.save(payment);
        }

        return PaymentVerifyDTO.builder()
                .paymentId(payment.getId())
                .paid(result.paid())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .providerReference(payment.getProviderReference())
                .build();
    }

    /**
     * Reconciles a provider webhook. Signature is verified first, then the event
     * is matched to a pending payment by provider + provider reference:
     * <ul>
     *   <li>settled + amount matches → mark COMPLETED (auto-link);</li>
     *   <li>settled + amount mismatch → never settle — the payment stays PENDING
     *       with providerStatus {@code AMOUNT_MISMATCH} for staff review;</li>
     *   <li>{@code charge.failed} → mark the pending payment FAILED;</li>
     *   <li>settled + no matching payment → enqueue an unmatched-funds item for
     *       staff reconciliation (the webhook carries no business identifier, so
     *       the money cannot be auto-attributed).</li>
     * </ul>
     */
    @Transactional
    public void processWebhook(String providerName, String rawPayload, Map<String, String> headers) {
        PaymentProvider provider = registry.require(providerName);
        if (!provider.verifyWebhookSignature(rawPayload, headers)) {
            throw new BusinessException("Invalid payment webhook signature", "INVALID_WEBHOOK_SIGNATURE");
        }
        PaymentProvider.WebhookEvent event = provider.parseWebhook(rawPayload, headers);
        if (!StringUtils.hasText(event.providerReference())) {
            throw new BusinessException("Webhook is missing a provider reference", "INVALID_WEBHOOK");
        }

        paymentRepository.findByProviderAndProviderReference(provider.getName(), event.providerReference())
                .ifPresentOrElse(
                        payment -> reconcile(payment, event),
                        () -> {
                            if (event.chargeSucceeded()) {
                                reconciliationService.recordUnmatched(provider.getName(), event);
                            }
                        });
    }

    private void reconcile(OrderPayment payment, PaymentProvider.WebhookEvent event) {
        if (event.chargeFailed() && !"COMPLETED".equals(payment.getStatus())) {
            if (!"FAILED".equals(payment.getStatus())) {
                payment.setStatus("FAILED");
                payment.setProviderStatus("FAILED");
                paymentRepository.save(payment);
                log.warn("Provider charge failed payment={} order={}", payment.getId(), payment.getOrderId());
                publishPaymentEvent(
                        payment.getBusinessId(), payment.getId(), payment.getOrderId(), "FAILED", payment.getProvider(),
                        payment.getProviderReference(), payment.getAmount(), "Provider charge failed");
            }
            return;
        }
        if (!event.chargeSucceeded() || "COMPLETED".equals(payment.getStatus())) {
            return;
        }
        boolean amountMatches = event.amount() == null || event.amount().compareTo(BigDecimal.ZERO) <= 0
                || event.amount().compareTo(payment.getAmount()) == 0;
        if (!amountMatches) {
            payment.setProviderStatus("AMOUNT_MISMATCH");
            paymentRepository.save(payment);
            log.warn("Webhook amount {} does not match pending payment {} (={}) — not settling",
                    event.amount(), payment.getId(), payment.getAmount());
            return;
        }
        settlePayment(payment, "SUCCESS");
    }

    /** Routes a refund for a provider-backed payment (used by the refund flow). */
    @Transactional
    public PaymentProvider.RefundResult refundPayment(OrderPayment payment) {
        PaymentProvider provider = registry.require(payment.getProvider());
        PaymentProvider.Connection connection = configService.resolveConnection(payment.getBusinessId(), provider);
        if (!provider.isConfigured() && connection.credentials() == null) {
            throw new BusinessException(payment.getProvider() + " is not configured", "PROVIDER_NOT_CONFIGURED",
                    "provider");
        }
        return provider.refund(new PaymentProvider.RefundRequest(payment.getProviderReference(),
                payment.getAmount(), "POS order refund", payment.getId().toString()), connection);
    }

    // ------------------------------------------------------------- sweep

    /**
     * Marks abandoned PENDING authorizations as FAILED/EXPIRED. Only authorizations
     * where both {@code status} and {@code providerStatus} are {@code PENDING} are
     * swept — staff-review states like {@code AMOUNT_MISMATCH} are preserved.
     *
     * @param cutoff the instant at (or after) which a pending payment is considered abandoned
     * @return the number of payments expired
     */
    @Transactional
    public int sweepExpiredPendingBefore(LocalDateTime cutoff) {
        List<OrderPayment> candidates = paymentRepository.findExpiredPendingBefore(cutoff);
        int expired = 0;
        for (OrderPayment payment : candidates) {
            if (!"PENDING".equals(payment.getStatus()) || !"PENDING".equals(payment.getProviderStatus())) {
                continue;
            }
            payment.setStatus("FAILED");
            payment.setProviderStatus("EXPIRED");
            paymentRepository.save(payment);

            Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null) {
                OrderTimeline timeline = new OrderTimeline();
                timeline.setOrder(order);
                timeline.setType("PAYMENT");
                timeline.setDescription("Payment request of " + payment.getAmount() + " "
                        + payment.getMethod() + " expired without authorization");
                timeline.setTimestamp(LocalDateTime.now());
                timeline.setUserName("System");
                order.getTimeline().add(timeline);
                orderRepository.save(order);
            }

            publishPaymentEvent(
                    payment.getBusinessId(), payment.getId(), payment.getOrderId(),
                    "FAILED", payment.getProvider(), payment.getProviderReference(),
                    payment.getAmount(), "Payment request expired without authorization");
            expired++;
        }
        if (expired > 0) {
            log.info("Swept {} abandoned PENDING payment(s) older than {}", expired, cutoff);
        }
        return expired;
    }

    @Scheduled(cron = "${app.payments.pending-expiry-cron:0 15 3 * * *}")
    void sweepExpiredPending() {
        try {
            sweepExpiredPendingBefore(LocalDateTime.now().minusHours(pendingExpiryHours));
        } catch (Exception e) {
            log.error("PENDING payment sweep failed", e);
        }
    }

    private void settlePayment(OrderPayment payment, String providerStatus) {
        payment.setStatus("COMPLETED");
        payment.setProviderStatus(providerStatus);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) {
            return;
        }
        BigDecimal totalPaid = paymentRepository.sumCompletedPaymentsByOrderId(order.getId());
        BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        order.setPaidAmount(totalPaid);
        order.setBalanceDue(totalPaid.compareTo(orderTotal) >= 0 ? BigDecimal.ZERO : orderTotal.subtract(totalPaid));
        orderRepository.save(order);
        log.info("Provider charge settled payment={} order={}", payment.getId(), order.getId());

        publishPaymentEvent(
                payment.getBusinessId(), payment.getId(), payment.getOrderId(), "COMPLETED", payment.getProvider(),
                payment.getProviderReference(), payment.getAmount(), "Provider charge settled");
    }

    private void publishPaymentEvent(UUID businessId, UUID paymentId, UUID orderId, String status, String provider,
            String providerReference, BigDecimal amount, String message) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("orderId", orderId);
        payload.put("status", status);
        payload.put("provider", provider);
        payload.put("providerReference", providerReference);
        payload.put("amount", amount);
        payload.put("message", message);
        webSocketPublisher.publishPaymentUpdate(businessId, orderId, payload);
    }

    private UUID getCurrentUserId() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername())
                    .map(User::getId)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("User " + userId.toString().substring(0, 8));
    }
}