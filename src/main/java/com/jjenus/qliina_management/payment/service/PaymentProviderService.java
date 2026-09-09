package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.payment.dto.PaymentProviderDTO;
import com.jjenus.qliina_management.payment.dto.PaymentVerifyDTO;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.provider.PaymentProvider;
import com.jjenus.qliina_management.payment.provider.PaymentProviderRegistry;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the online (provider-backed) segment of the POS checkout:
 * provider availability, charge initiation, endpoint verification, webhook
 * reconciliation and provider refunds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderService {

    private final PaymentProviderRegistry registry;
    private final PaymentProviderConfigService configService;
    private final OrderPaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Value("${app.payments.currency:NGN}")
    private String currency;

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
        return provider.charge(new PaymentProvider.ChargeRequest(
                amount, currency, customerEmail, customerName, reference, null, connection));
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
        PaymentProvider.VerifyResult result = provider.verify(payment.getProviderReference());

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

    /** Reconciles a subscription webhook: marks settled provider charges completed. */
    @Transactional
    public void processWebhook(String providerName, String rawPayload, java.util.Map<String, String> headers) {
        PaymentProvider provider = registry.require(providerName);
        if (!provider.verifyWebhookSignature(rawPayload, headers)) {
            throw new BusinessException("Invalid payment webhook signature", "INVALID_WEBHOOK_SIGNATURE");
        }
        PaymentProvider.WebhookEvent event = provider.parseWebhook(rawPayload, headers);
        if (!StringUtils.hasText(event.providerReference())) {
            throw new BusinessException("Webhook is missing a provider reference", "INVALID_WEBHOOK");
        }

        paymentRepository.findByProviderAndProviderReference(provider.getName(), event.providerReference())
                .ifPresent(payment -> {
                    if (event.chargeSucceeded() && !"COMPLETED".equals(payment.getStatus())) {
                        settlePayment(payment, "SUCCESS");
                    }
                });
    }

    /** Routes a refund for a provider-backed payment (used by the refund flow). */
    @Transactional
    public PaymentProvider.RefundResult refundPayment(OrderPayment payment) {
        PaymentProvider provider = registry.require(payment.getProvider());
        if (!provider.isConfigured()) {
            throw new BusinessException(payment.getProvider() + " is not configured", "PROVIDER_NOT_CONFIGURED",
                    "provider");
        }
        return provider.refund(new PaymentProvider.RefundRequest(payment.getProviderReference(),
                payment.getAmount(), "POS order refund", payment.getId().toString()));
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
    }
}