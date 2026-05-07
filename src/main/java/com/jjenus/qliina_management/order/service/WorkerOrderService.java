// ./src/main/java/com/jjenus/qliina_management/order/service/WorkerOrderService.java
package com.jjenus.qliina_management.order.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.IdGenerator;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.dto.WorkerItemDTO;
import com.jjenus.qliina_management.order.model.*;
import com.jjenus.qliina_management.order.repository.ItemWorkerInteractionRepository;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles worker-scoped order item operations.
 *
 * IMPORTANT DOMAIN DISTINCTION:
 * - Item-level statuses (OrderItem.ItemStatus): processing state of individual garments
 * - Order-level statuses (Order.OrderStatus): overall state including delivery readiness
 *
 * Delivery workers operate at the ORDER level, not item level.
 * Items become COMPLETED after QC, then the ORDER becomes READY_FOR_PICKUP
 * when all items are done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerOrderService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ItemWorkerInteractionRepository interactionRepository;

    /**
     * Role-based item status transitions.
     * Key: current item status → next item status.
     *
     * Note: DELIVERY is NOT here because delivery workers handle orders, not items.
     * They use the Order-level statuses (READY_FOR_PICKUP → OUT_FOR_DELIVERY → COMPLETED).
     */
    private static final Map<String, Map<OrderItem.ItemStatus, OrderItem.ItemStatus>> ROLE_ITEM_TRANSITIONS = Map.of(
        "WASHER", Map.of(
            OrderItem.ItemStatus.RECEIVED, OrderItem.ItemStatus.WASHING,
            OrderItem.ItemStatus.WASHING, OrderItem.ItemStatus.WASHED
        ),
        "IRONER", Map.of(
            OrderItem.ItemStatus.WASHED, OrderItem.ItemStatus.IRONING,
            OrderItem.ItemStatus.IRONING, OrderItem.ItemStatus.IRONED
        )
    );

    /**
     * Statuses where items WAIT for a role to pick them up.
     * Delivery workers see orders in READY_FOR_PICKUP status (handled in Order-level queries).
     */
    private static final Map<String, List<OrderItem.ItemStatus>> ROLE_INCOMING = Map.of(
        "WASHER", List.of(OrderItem.ItemStatus.RECEIVED),
        "IRONER", List.of(OrderItem.ItemStatus.WASHED)
    );

    /** Roles that can use item-level operations */
    private static final Set<String> ITEM_WORKER_ROLES = Set.of("WASHER", "IRONER");

    @Transactional(readOnly = true)
    public WorkerItemDTO lookupItem(UUID businessId, UUID workerId, String itemId) {
        OrderItem item = findItem(businessId, itemId);
        User worker = getUser(workerId);
        String role = getPrimaryRole(worker);

        if (!businessId.equals(item.getOrder().getBusinessId())) {
            throw new BusinessException("Item not found in this business", "ITEM_NOT_FOUND");
        }

        recordInteraction(workerId, item.getId(), determineAccessMethod(itemId));

        return mapToWorkerItemDTO(item, role);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerItemDTO> getWorkQueue(
            UUID businessId, UUID workerId, String filter, Pageable pageable) {

        User worker = getUser(workerId);
        String role = getPrimaryRole(worker);

        // Delivery workers use order-level queries, not item-level
        if ("DELIVERY".equals(role)) {
            return getDeliveryQueue(businessId, worker.getPrimaryShopId(), pageable);
        }

        List<OrderItem.ItemStatus> relevantStatuses = ROLE_INCOMING.getOrDefault(role, List.of());
        if (relevantStatuses.isEmpty()) {
            return PageResponse.from(Page.empty());
        }

        Page<OrderItem> items = orderItemRepository.findByBusinessIdAndStatusIn(
                businessId, worker.getPrimaryShopId(), relevantStatuses, pageable);

        List<WorkerItemDTO> dtos = items.getContent().stream()
                .map(item -> mapToWorkerItemDTO(item, role))
                .collect(Collectors.toList());

        return PageResponse.from(new PageImpl<>(dtos, pageable, items.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerItemDTO> getWorkHistory(
            UUID businessId, UUID workerId, Pageable pageable) {

        User worker = getUser(workerId);
        String role = getPrimaryRole(worker);

        var interactions = interactionRepository.findByWorkerIdAndBusinessId(workerId, businessId, pageable);

        List<WorkerItemDTO> dtos = interactions.getContent().stream()
                .map(iwi -> {
                    OrderItem item = orderItemRepository.findById(iwi.getItemId()).orElse(null);
                    if (item == null) return null;
                    return mapToWorkerItemDTO(item, role);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return PageResponse.from(new PageImpl<>(dtos, pageable, interactions.getTotalElements()));
    }

    @Transactional
    public WorkerItemDTO startWorkOnItem(UUID businessId, UUID workerId, UUID itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));

        User worker = getUser(workerId);
        String role = getPrimaryRole(worker);

        if (!ITEM_WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                "Item-level operations are only available for Washer and Ironer roles. " +
                "Delivery workers should use order-level operations.",
                "NOT_ITEM_WORKER_ROLE"
            );
        }

        recordInteraction(workerId, itemId, "QUEUE");

        Map<OrderItem.ItemStatus, OrderItem.ItemStatus> transitions = 
            ROLE_ITEM_TRANSITIONS.getOrDefault(role, Map.of());
        OrderItem.ItemStatus nextStatus = transitions.get(item.getStatus());

        if (nextStatus == null) {
            throw new BusinessException(
                String.format("Cannot start work on item in '%s' status as '%s' role. Expected one of: %s",
                    item.getStatus(), role, transitions.keySet()),
                "INVALID_STATUS_TRANSITION"
            );
        }

        OrderItem.ItemStatus previousStatus = item.getStatus();
        item.setStatus(nextStatus);

        ItemStatusHistory history = new ItemStatusHistory();
        history.setOrderItem(item);
        history.setStatus(nextStatus.toString());
        history.setTimestamp(LocalDateTime.now());
        history.setUpdatedBy(workerId);
        history.setNotes(String.format("%s started work (%s → %s)", role, previousStatus, nextStatus));
        item.getStatusHistory().add(history);

        if (item.getBarcode() == null) {
            item.setBarcode(IdGenerator.generateQrCode("item"));
        }

        orderItemRepository.save(item);
        synchronizeOrderStatus(item.getOrder());

        return mapToWorkerItemDTO(item, role);
    }

    @Transactional
    public WorkerItemDTO completeWorkOnItem(
            UUID businessId, UUID workerId, UUID itemId, String notes) {

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));

        User worker = getUser(workerId);
        String role = getPrimaryRole(worker);

        if (!ITEM_WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                "Item-level operations are only available for Washer and Ironer roles.",
                "NOT_ITEM_WORKER_ROLE"
            );
        }

        recordInteraction(workerId, itemId, "COMPLETE");

        Map<OrderItem.ItemStatus, OrderItem.ItemStatus> transitions = 
            ROLE_ITEM_TRANSITIONS.getOrDefault(role, Map.of());
        OrderItem.ItemStatus nextStatus = transitions.get(item.getStatus());

        if (nextStatus == null) {
            throw new BusinessException(
                String.format("Cannot complete work on item in '%s' status as '%s' role. " +
                    "Item must be in one of: %s",
                    item.getStatus(), role, transitions.keySet()),
                "INVALID_STATUS_TRANSITION"
            );
        }

        OrderItem.ItemStatus previousStatus = item.getStatus();
        item.setStatus(nextStatus);

        ItemStatusHistory history = new ItemStatusHistory();
        history.setOrderItem(item);
        history.setStatus(nextStatus.toString());
        history.setTimestamp(LocalDateTime.now());
        history.setUpdatedBy(workerId);
        history.setNotes(notes != null ? notes : 
            String.format("%s completed work (%s → %s)", role, previousStatus, nextStatus));
        item.getStatusHistory().add(history);

        orderItemRepository.save(item);
        synchronizeOrderStatus(item.getOrder());

        return mapToWorkerItemDTO(item, role);
    }

    /**
     * Gets delivery queue at the ORDER level (not item level).
     * Delivery workers see orders ready for pickup/delivery.
     */
    private PageResponse<WorkerItemDTO> getDeliveryQueue(UUID businessId, UUID shopId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByBusinessIdAndShopIdAndStatusIn(
                businessId, shopId,
                List.of(Order.OrderStatus.READY_FOR_PICKUP, Order.OrderStatus.OUT_FOR_DELIVERY),
                pageable);

        List<WorkerItemDTO> dtos = orders.getContent().stream()
                .map(order -> {
                    // For delivery, represent the whole order as a single work item
                    return WorkerItemDTO.builder()
                            .id(order.getId())
                            .orderId(order.getId())
                            .orderNumber(order.getOrderNumber())
                            .serviceType("Delivery — " + order.getItemCount() + " items")
                            .quantity(order.getItemCount())
                            .status(order.getStatus().toString())
                            .priority(order.getPriority().toString())
                            .receivedAt(order.getReceivedAt())
                            .promisedDate(order.getPromisedDate())
                            .availableActions(List.of("START"))
                            .needsQualityCheck(false)
                            .build();
                })
                .collect(Collectors.toList());

        return PageResponse.from(new PageImpl<>(dtos, pageable, orders.getTotalElements()));
    }

    /**
     * Synchronizes parent order status based on all item statuses.
     *
     * After an item is IRONED and passes QC, it becomes COMPLETED.
     * When ALL items in an order are COMPLETED, the order becomes READY_FOR_PICKUP.
     */
    private void synchronizeOrderStatus(Order order) {
        Order refreshedOrder = orderRepository.findById(order.getId())
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));

        List<OrderItem> items = refreshedOrder.getItems();
        if (items.isEmpty()) return;

        // Check if all items have passed QC and are COMPLETED
        boolean allCompleted = items.stream()
                .allMatch(item -> item.getStatus() == OrderItem.ItemStatus.COMPLETED);

        if (allCompleted && refreshedOrder.getStatus() != Order.OrderStatus.READY_FOR_PICKUP) {
            Order.OrderStatus previousStatus = refreshedOrder.getStatus();
            refreshedOrder.setStatus(Order.OrderStatus.READY_FOR_PICKUP);
            refreshedOrder.setActualReadyAt(LocalDateTime.now());

            OrderTimeline timeline = new OrderTimeline();
            timeline.setOrder(refreshedOrder);
            timeline.setType("STATUS_CHANGE");
            timeline.setStatus(Order.OrderStatus.READY_FOR_PICKUP.toString());
            timeline.setDescription(
                String.format("All items completed. Order ready for pickup (was: %s)", previousStatus));
            timeline.setTimestamp(LocalDateTime.now());
            refreshedOrder.getTimeline().add(timeline);

            orderRepository.save(refreshedOrder);
            return;
        }

        // Otherwise, check if all items are in the same status
        Set<OrderItem.ItemStatus> uniqueStatuses = items.stream()
                .map(OrderItem::getStatus)
                .collect(Collectors.toSet());

        if (uniqueStatuses.size() == 1) {
            OrderItem.ItemStatus unifiedStatus = uniqueStatuses.iterator().next();
            Order.OrderStatus mappedStatus = mapItemStatusToOrderStatus(unifiedStatus);

            if (mappedStatus != null && mappedStatus != refreshedOrder.getStatus()) {
                Order.OrderStatus previousOrderStatus = refreshedOrder.getStatus();
                refreshedOrder.setStatus(mappedStatus);

                OrderTimeline timeline = new OrderTimeline();
                timeline.setOrder(refreshedOrder);
                timeline.setType("STATUS_CHANGE");
                timeline.setStatus(mappedStatus.toString());
                timeline.setDescription(String.format(
                    "All items moved to '%s' — order status updated from '%s'",
                    unifiedStatus, previousOrderStatus));
                timeline.setTimestamp(LocalDateTime.now());
                refreshedOrder.getTimeline().add(timeline);

                orderRepository.save(refreshedOrder);
            }
        }
    }

    private Order.OrderStatus mapItemStatusToOrderStatus(OrderItem.ItemStatus itemStatus) {
        return switch (itemStatus) {
            case RECEIVED -> Order.OrderStatus.RECEIVED;
            case WASHING -> Order.OrderStatus.WASHING;
            case WASHED -> Order.OrderStatus.WASHED;
            case IRONING -> Order.OrderStatus.IRONING;
            case IRONED -> Order.OrderStatus.IRONED;
            case QUALITY_CHECK -> Order.OrderStatus.QUALITY_CHECK;
            default -> null;
        };
    }

    private OrderItem findItem(UUID businessId, String itemId) {

        if (itemId == null || itemId.trim().isEmpty()) {
            throw new BusinessException("Item ID is required", "INVALID_ITEM_ID");
        }

        itemId = itemId.replaceAll("\\s+", "").toUpperCase();
    
        // 1. Try UUID (internal system use)
        try {
            UUID uuid = UUID.fromString(itemId);
            return orderItemRepository.findById(uuid)
                    .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID → continue
        }
    
        // 2. Validate checksum BEFORE hitting DB
        String id = itemId.contains("-") ? itemId.substring(3) : itemId;
        log.debug("Item ID: {}", id);

        if (!IdGenerator.isValidWithChecksum(id)) {
            throw new BusinessException("Invalid item ID format", "INVALID_ITEM_ID");
        }
    
        // 3. Safe to query
        return orderItemRepository.findByBusinessIdAndCode(businessId, itemId).orElseThrow(()-> new BusinessException("Item ID not found", "ITEM_NOTFOUND"));
    }

    private String determineAccessMethod(String itemId) {

        if (itemId == null || itemId.trim().isEmpty()) {
            return "INVALID_INPUT";
        }
    
        itemId = itemId.replaceAll("\\s+", "").toUpperCase();
    
        // 1. UUID → internal system usage
        try {
            UUID.fromString(itemId);
            return "UUID_AUTOMATED_LOOKUP";
        } catch (IllegalArgumentException ignored) {}
    
        // 2. Checksum-valid ID → trusted input
        if (IdGenerator.isValidWithChecksum(itemId)) {
            return itemId.startsWith("QL-") ? "QR_SCAN" : "MANUAL_ENTRY_VALID";
        }
    
        // 3. Fallback → invalid / mistyped
        return "MANUAL_ENTRY_INVALID";
    }

    private void recordInteraction(UUID workerId, UUID itemId, String accessMethod) {
        var existing = interactionRepository.findByWorkerIdAndItemId(workerId, itemId);
        if (existing.isPresent()) {
            ItemWorkerInteraction iwi = existing.get();
            iwi.recordInteraction();
            interactionRepository.save(iwi);
        } else {
            ItemWorkerInteraction iwi = new ItemWorkerInteraction();
            iwi.setWorkerId(workerId);
            iwi.setItemId(itemId);
            iwi.setFirstInteraction(LocalDateTime.now());
            iwi.setLastInteraction(LocalDateTime.now());
            iwi.setInteractionCount(1);
            iwi.setFirstAccessMethod(accessMethod);
            interactionRepository.save(iwi);
        }
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
    }

    private String getPrimaryRole(User user) {
        return user.getRoles().stream()
                .findFirst()
                .map(ur -> ur.getRole().getName())
                .orElseThrow(() -> new BusinessException("No role assigned", "NO_ROLE"));
    }

    private WorkerItemDTO mapToWorkerItemDTO(OrderItem item, String currentRole) {
        Map<OrderItem.ItemStatus, OrderItem.ItemStatus> transitions = 
            ROLE_ITEM_TRANSITIONS.getOrDefault(currentRole, Map.of());

        List<String> availableActions = new ArrayList<>();
        if (transitions.containsKey(item.getStatus())) {
            availableActions.add("START");
        }

        boolean needsQC = item.getStatus() == OrderItem.ItemStatus.IRONED;

        return WorkerItemDTO.builder()
                .id(item.getId())
                .shortId(item.getBarcode())
                .orderId(item.getOrder().getId())
                .orderNumber(item.getOrder().getOrderNumber())
                .serviceType(item.getServiceType())
                .garmentType(item.getGarmentType())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .weight(item.getWeight())
                .status(item.getStatus().toString())
                .specialInstructions(item.getSpecialInstructions())
                .images(item.getImages())
                .priority(item.getOrder().getPriority().toString())
                .receivedAt(item.getOrder().getReceivedAt())
                .promisedDate(item.getOrder().getPromisedDate())
                .availableActions(availableActions)
                .needsQualityCheck(needsQC)
                .build();
    }
}