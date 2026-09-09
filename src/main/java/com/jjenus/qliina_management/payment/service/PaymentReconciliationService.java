package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.payment.dto.PaymentReconciliationItemDTO;
import com.jjenus.qliina_management.payment.dto.ResolveReconciliationRequest;
import com.jjenus.qliina_management.payment.model.PaymentReconciliationItem;
import com.jjenus.qliina_management.payment.provider.PaymentProvider;
import com.jjenus.qliina_management.payment.repository.PaymentReconciliationItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unmatched-funds queue: settled provider webhooks with no matching payment
 * record wait here (OPEN, cross-tenant by design — the webhook URL carries no
 * business identifier) until an admin resolves them to a business + order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentReconciliationItemRepository repository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * Upserts an OPEN queue item keyed on (provider, provider reference) without
     * ambiguity — the same settled transfer arriving twice is one item, refreshed.
     */
    @Transactional
    public void recordUnmatched(String provider, PaymentProvider.WebhookEvent event) {
        PaymentReconciliationItem item = repository
                .findByProviderAndProviderReference(provider, event.providerReference())
                .orElseGet(() -> {
                    PaymentReconciliationItem created = new PaymentReconciliationItem();
                    created.setProvider(provider);
                    created.setProviderReference(event.providerReference());
                    created.setStatus("OPEN");
                    return created;
                });
        item.setAmount(event.amount());
        item.setRawEvent(event.rawPayload());
        item.setReceivedAt(LocalDateTime.now());
        repository.save(item);
        log.info("Unmatched provider funds queued provider={} reference={} amount={}",
                provider, event.providerReference(), event.amount());
    }

    /**
     * OPEN items are visible to every business (global queue until resolved);
     * RESOLVED items are scoped to the resolving business. An explicit status
     * filter narrows one side of that base set.
     */
    @Transactional(readOnly = true)
    public PageResponse<PaymentReconciliationItemDTO> list(UUID businessId, String status, Pageable pageable) {
        List<PaymentReconciliationItem> items;
        if ("OPEN".equalsIgnoreCase(status)) {
            items = repository.findByStatusOrderByReceivedAtDesc("OPEN");
        } else if ("RESOLVED".equalsIgnoreCase(status)) {
            items = repository.findByBusinessIdAndStatusOrderByReceivedAtDesc(businessId, "RESOLVED");
        } else {
            items = new ArrayList<>(repository.findByStatusOrderByReceivedAtDesc("OPEN"));
            items.addAll(repository.findByBusinessIdAndStatusOrderByReceivedAtDesc(businessId, "RESOLVED"));
            items.sort((a, b) -> b.getReceivedAt().compareTo(a.getReceivedAt()));
        }
        Page<PaymentReconciliationItemDTO> page = new PageImpl<>(items.stream().map(this::toDTO).toList(),
                pageable, items.size());
        return PageResponse.from(page);
    }

    /** Links an OPEN item to a specific order (and therefore business). */
    @Transactional
    public PaymentReconciliationItemDTO resolve(UUID businessId, UUID itemId,
            ResolveReconciliationRequest request) {
        PaymentReconciliationItem item = repository.findById(itemId)
                .orElseThrow(() -> new BusinessException("Reconciliation item not found",
                        "RECONCILIATION_NOT_FOUND"));
        if (item.getBusinessId() != null && !item.getBusinessId().equals(businessId)) {
            throw new BusinessException("Reconciliation item not found", "RECONCILIATION_NOT_FOUND");
        }
        if ("RESOLVED".equals(item.getStatus())) {
            throw new BusinessException("Reconciliation item is already resolved", "RECONCILIATION_ALREADY_RESOLVED");
        }
        if (request.getOrderId() != null) {
            Order order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
            if (!order.getBusinessId().equals(businessId)) {
                throw new BusinessException("Order not found", "ORDER_NOT_FOUND");
            }
            item.setOrderId(order.getId());
        }
        item.setBusinessId(businessId);
        item.setStatus("RESOLVED");
        item.setNotes(request.getNotes());
        item.setResolvedBy(getCurrentUserId());
        item.setResolvedAt(LocalDateTime.now());
        repository.save(item);
        log.info("Reconciliation item {} resolved by business {} to order {}",
                itemId, businessId, item.getOrderId());
        return toDTO(item);
    }

    private PaymentReconciliationItemDTO toDTO(PaymentReconciliationItem item) {
        return PaymentReconciliationItemDTO.builder()
                .id(item.getId())
                .provider(item.getProvider())
                .providerReference(item.getProviderReference())
                .amount(item.getAmount())
                .status(item.getStatus())
                .receivedAt(item.getReceivedAt())
                .businessId(item.getBusinessId())
                .orderId(item.getOrderId())
                .notes(item.getNotes())
                .resolvedBy(item.getResolvedBy())
                .resolvedAt(item.getResolvedAt())
                .build();
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
}