// ./src/main/java/com/jjenus/qliina_management/order/service/OrderDiscrepancyService.java
package com.jjenus.qliina_management.order.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.notification.dto.SendNotificationRequest;
import com.jjenus.qliina_management.notification.service.NotificationOrchestrator;
import com.jjenus.qliina_management.order.dto.CreateDiscrepancyRequest;
import com.jjenus.qliina_management.order.dto.OrderDiscrepancyDTO;
import com.jjenus.qliina_management.order.dto.ReviewDiscrepancyRequest;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.model.OrderDiscrepancy;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.repository.OrderDiscrepancyRepository;
import com.jjenus.qliina_management.order.repository.OrderItemRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Worker → front desk / managerial discrepancy reporting.
 *
 * Workers report problems found while processing an order (code mismatch,
 * count off, damaged or missing pieces). Managerial roles get notified and
 * work the report to resolution; the reporter is notified of every decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDiscrepancyService {

    private static final List<String> HANDLER_ROLES =
            List.of("BUSINESS_ADMIN", "SHOP_MANAGER", "FRONT_DESK");

    private final OrderDiscrepancyRepository discrepancyRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;

    // ── worker side ──────────────────────────────────────────────────────────

    @Transactional
    public OrderDiscrepancyDTO create(UUID businessId, UUID reporterId, CreateDiscrepancyRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        if (!businessId.equals(order.getBusinessId())) {
            throw new BusinessException("Order not found in this business", "ORDER_NOT_FOUND");
        }

        OrderDiscrepancy.DiscrepancyType type = parseType(request.getType());

        String itemCode = null;
        if (request.getOrderItemId() != null) {
            OrderItem item = orderItemRepository.findById(request.getOrderItemId())
                    .orElseThrow(() -> new BusinessException("Order item not found", "ITEM_NOT_FOUND"));
            if (!item.getOrder().getId().equals(order.getId())) {
                throw new BusinessException("Item does not belong to this order", "ITEM_NOT_FOUND");
            }
            itemCode = item.getItemNumber();
        }

        OrderDiscrepancy d = new OrderDiscrepancy();
        d.setOrder(order);
        d.setOrderItemId(request.getOrderItemId());
        d.setReportedBy(reporterId);
        d.setType(type);
        d.setDescription(request.getDescription());
        d.setStatus(OrderDiscrepancy.DiscrepancyStatus.OPEN);
        d = discrepancyRepository.save(d);

        notifyHandlers(businessId, order.getOrderNumber(), type, request.getDescription(), reporterId);

        log.info("Discrepancy {} reported by {} on order {} ({})",
                d.getId(), reporterId, order.getOrderNumber(), type);
        return toDTO(d);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDiscrepancyDTO> myReports(UUID businessId, UUID reporterId, Pageable pageable) {
        Page<OrderDiscrepancy> page = discrepancyRepository.findByBusinessIdAndReportedBy(
                businessId, reporterId, pageable);
        return PageResponse.from(new PageImpl<>(
                page.getContent().stream().map(this::toDTO).toList(),
                pageable, page.getTotalElements()));
    }

    // ── front desk / manager side ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<OrderDiscrepancyDTO> list(UUID businessId, String status, Pageable pageable) {
        Page<OrderDiscrepancy> page;
        if (status != null && !status.isBlank()) {
            page = discrepancyRepository.findByBusinessIdAndStatus(
                    businessId, OrderDiscrepancy.DiscrepancyStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = discrepancyRepository.findByBusinessId(businessId, pageable);
        }
        return PageResponse.from(new PageImpl<>(
                page.getContent().stream().map(this::toDTO).toList(),
                pageable, page.getTotalElements()));
    }

    public long countOpen(UUID businessId) {
        return discrepancyRepository.countByBusinessIdAndStatus(
                businessId, OrderDiscrepancy.DiscrepancyStatus.OPEN);
    }

    @Transactional
    public OrderDiscrepancyDTO review(UUID businessId, UUID handlerId, UUID discrepancyId,
                                      ReviewDiscrepancyRequest request) {
        OrderDiscrepancy d = discrepancyRepository.findById(discrepancyId)
                .orElseThrow(() -> new BusinessException("Discrepancy not found", "DISCREPANCY_NOT_FOUND"));
        if (!businessId.equals(d.getBusinessId())) {
            throw new BusinessException("Discrepancy not found in this business", "DISCREPANCY_NOT_FOUND");
        }
        if (d.getStatus() == OrderDiscrepancy.DiscrepancyStatus.RESOLVED
                || d.getStatus() == OrderDiscrepancy.DiscrepancyStatus.DISMISSED) {
            throw new BusinessException(
                    "Discrepancy is already " + d.getStatus(), "INVALID_STATUS");
        }

        switch (request.getAction().toUpperCase()) {
            case "ACKNOWLEDGE" -> d.setStatus(OrderDiscrepancy.DiscrepancyStatus.ACKNOWLEDGED);
            case "RESOLVE" -> {
                d.setStatus(OrderDiscrepancy.DiscrepancyStatus.RESOLVED);
                d.setResolvedAt(LocalDateTime.now());
            }
            case "DISMISS" -> {
                d.setStatus(OrderDiscrepancy.DiscrepancyStatus.DISMISSED);
                d.setResolvedAt(LocalDateTime.now());
            }
            default -> throw new BusinessException(
                    "Unknown action: " + request.getAction(), "INVALID_ACTION");
        }

        d.setHandledBy(handlerId);
        d.setHandlingNote(request.getNote());
        d = discrepancyRepository.save(d);

        notifyReporter(d, handlerId, request.getAction().toUpperCase());
        return toDTO(d);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private OrderDiscrepancy.DiscrepancyType parseType(String type) {
        try {
            return OrderDiscrepancy.DiscrepancyType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Type must be CODE_MISMATCH, COUNT_MISMATCH, DAMAGED, MISSING_ITEM or OTHER",
                    "INVALID_TYPE");
        }
    }

    private void notifyHandlers(UUID businessId, String orderNumber,
                                OrderDiscrepancy.DiscrepancyType type,
                                String description, UUID reporterId) {
        try {
            List<UUID> recipientIds = userRepository
                    .findByBusinessIdAndRoles(businessId, HANDLER_ROLES).stream()
                    .map(User::getId)
                    .toList();
            if (recipientIds.isEmpty()) return;
            SendNotificationRequest notification = new SendNotificationRequest();
            notification.setRecipients(recipientIds);
            notification.setType("DISCREPANCY");
            notification.setChannel("IN_APP");
            notification.setTitle(String.format("Discrepancy on %s: %s", orderNumber, type));
            notification.setBody(String.format("%s: %s", getUserName(reporterId), description));
            notification.setPriority(type == OrderDiscrepancy.DiscrepancyType.MISSING_ITEM ? "HIGH" : "NORMAL");
            notificationOrchestrator.sendNotification(businessId, notification);
        } catch (Exception e) {
            log.warn("Failed to notify handlers about discrepancy: {}", e.getMessage());
        }
    }

    private void notifyReporter(OrderDiscrepancy d, UUID handlerId, String action) {
        try {
            SendNotificationRequest notification = new SendNotificationRequest();
            notification.setRecipients(List.of(d.getReportedBy()));
            notification.setType("DISCREPANCY");
            notification.setChannel("IN_APP");
            notification.setTitle(String.format("Report %s: %s",
                    action.toLowerCase(), d.getOrder().getOrderNumber()));
            notification.setBody(String.format("Your discrepancy report was %s by %s.",
                    action.toLowerCase(), getUserName(handlerId)));
            notification.setPriority("NORMAL");
            notificationOrchestrator.sendNotification(d.getBusinessId(), notification);
        } catch (Exception e) {
            log.warn("Failed to notify reporter about discrepancy decision: {}", e.getMessage());
        }
    }

    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("User");
    }

    private OrderDiscrepancyDTO toDTO(OrderDiscrepancy d) {
        String itemCode = null;
        if (d.getOrderItemId() != null) {
            itemCode = orderItemRepository.findById(d.getOrderItemId())
                    .map(OrderItem::getItemNumber).orElse(null);
        }
        return OrderDiscrepancyDTO.builder()
                .id(d.getId())
                .orderId(d.getOrder().getId())
                .orderNumber(d.getOrder().getOrderNumber())
                .orderItemId(d.getOrderItemId())
                .itemCode(itemCode)
                .reportedBy(d.getReportedBy())
                .reportedByName(getUserName(d.getReportedBy()))
                .type(d.getType().toString())
                .description(d.getDescription())
                .status(d.getStatus().toString())
                .handledBy(d.getHandledBy())
                .handledByName(getUserName(d.getHandledBy()))
                .handlingNote(d.getHandlingNote())
                .resolvedAt(d.getResolvedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
