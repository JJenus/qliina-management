// ./src/main/java/com/jjenus/qliina_management/order/service/WorkerOrderService.java
package com.jjenus.qliina_management.order.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.IdGenerator;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.employee.service.ShiftGateService;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.dto.WorkerItemDTO;
import com.jjenus.qliina_management.order.model.*;
import com.jjenus.qliina_management.order.repository.ItemWorkerInteractionRepository;
import com.jjenus.qliina_management.order.repository.OrderItemRepository;
import com.jjenus.qliina_management.order.repository.OrderItemUnitRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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
    private final OrderItemUnitRepository orderItemUnitRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ItemWorkerInteractionRepository interactionRepository;
    private final EmployeeShiftRepository shiftRepository;
    private final ShiftGateService shiftGateService;

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

    @Transactional
    public WorkerItemDTO lookupItem(UUID businessId, UUID workerId, String itemId) {
        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        OrderItem item = findItem(businessId, itemId);

        if (!businessId.equals(item.getOrder().getBusinessId())) {
            throw new BusinessException("Item not found in this business", "ITEM_NOT_FOUND");
        }

        recordInteraction(workerId, item.getId(), determineAccessMethod(itemId));

        return mapToWorkerItemDTO(item, role);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerItemDTO> getWorkQueue(
            UUID businessId, UUID workerId, String filter, Pageable pageable) {

        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        // Delivery workers use order-level queries, not item-level
        if ("DELIVERY".equals(role)) {
            return getDeliveryQueue(businessId, getUser(workerId).getPrimaryShopId(), pageable);
        }

        List<OrderItem.ItemStatus> relevantStatuses = ROLE_INCOMING.getOrDefault(role, List.of());
        if (relevantStatuses.isEmpty()) {
            return PageResponse.from(Page.empty());
        }

        // Queue shows both items waiting for the role AND items the role has
        // already started (previously started items vanished from the queue).
        boolean washerMode = "WASHER".equals(role);
        boolean ironerMode = "IRONER".equals(role);
        List<OrderItem.ItemStatus> statuses = new ArrayList<>(relevantStatuses);
        if (washerMode) {
            statuses.add(OrderItem.ItemStatus.WASHING);
        } else if (ironerMode) {
            statuses.add(OrderItem.ItemStatus.IRONING);
            // Iron-only garments queue for ironing straight from reception.
            statuses.add(OrderItem.ItemStatus.RECEIVED);
        }

        Page<OrderItem> items = orderItemRepository.findRoleWorkQueue(
                businessId, getUser(workerId).getPrimaryShopId(), statuses,
                OrderItem.ItemStatus.RECEIVED, washerMode, ironerMode, pageable);

        List<WorkerItemDTO> dtos = items.getContent().stream()
                .map(item -> mapToWorkerItemDTO(item, role))
                .collect(Collectors.toList());

        return PageResponse.from(new PageImpl<>(dtos, pageable, items.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerItemDTO> getWorkHistory(
            UUID businessId, UUID workerId, Pageable pageable) {

        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

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
        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));

        if (!ITEM_WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                "Item-level operations are only available for Washer and Ironer roles. " +
                "Delivery workers should use order-level operations.",
                "NOT_ITEM_WORKER_ROLE"
            );
        }

        recordInteraction(workerId, itemId, "QUEUE");

        OrderItem.ItemStatus nextStatus = resolveNextStatus(role, item);

        if (nextStatus == null) {
            throw new BusinessException(
                String.format("Cannot start work on item in '%s' status as '%s' role.%s",
                    item.getStatus(), role,
                    !needsWashing(item) && "WASHER".equals(role)
                        ? " This item is iron-only and skips washing."
                        : ""),
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
        synchronizeOrderStatus(item.getOrder(), workerId);

        return mapToWorkerItemDTO(item, role);
    }

    @Transactional
    public WorkerItemDTO completeWorkOnItem(
            UUID businessId, UUID workerId, UUID itemId, String notes) {

        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));

        if (!ITEM_WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                "Item-level operations are only available for Washer and Ironer roles.",
                "NOT_ITEM_WORKER_ROLE"
            );
        }

        recordInteraction(workerId, itemId, "COMPLETE");

        OrderItem.ItemStatus nextStatus = resolveNextStatus(role, item);

        if (nextStatus == null) {
            throw new BusinessException(
                String.format("Cannot complete work on item in '%s' status as '%s' role. " +
                    "Item must be in one of: %s",
                    item.getStatus(), role,
                    ROLE_ITEM_TRANSITIONS.getOrDefault(role, Map.of()).keySet()),
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
        synchronizeOrderStatus(item.getOrder(), workerId);

        return mapToWorkerItemDTO(item, role);
    }

    /**
     * Applies a quality-check outcome to an item.
     * PASS  → IRONED/QUALITY_CHECK items become COMPLETED (order may become READY_FOR_PICKUP).
     * FAIL  → item is sent back to WASHING for rework (rewash loop), counted as rework downstream.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyQualityOutcome(UUID businessId, UUID itemId, boolean passed, UUID checkedBy) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));

        if (!businessId.equals(item.getOrder().getBusinessId())) {
            throw new BusinessException("Item not found in this business", "ITEM_NOT_FOUND");
        }

        if (passed) {
            if (item.getStatus() != OrderItem.ItemStatus.IRONED
                    && item.getStatus() != OrderItem.ItemStatus.QUALITY_CHECK) {
                return;
            }
            item.setStatus(OrderItem.ItemStatus.COMPLETED);
            addStatusHistory(item, OrderItem.ItemStatus.COMPLETED, checkedBy,
                    "QC passed — item completed");
        } else {
            if (item.getStatus() == OrderItem.ItemStatus.COMPLETED
                    || item.getStatus() == OrderItem.ItemStatus.RECEIVED) {
                return;
            }
            // Rework goes back to washing — but iron-only garments never see
            // the washer, so they return to ironing instead.
            boolean rewash = needsWashing(item);
            item.setStatus(rewash ? OrderItem.ItemStatus.WASHING : OrderItem.ItemStatus.IRONING);
            addStatusHistory(item, item.getStatus(), checkedBy,
                    rewash ? "QC failed — sent for rework/rewash" : "QC failed — returned for re-ironing");
        }

        orderItemRepository.save(item);
        synchronizeOrderStatus(item.getOrder(), checkedBy);
    }

    private void addStatusHistory(OrderItem item, OrderItem.ItemStatus status, UUID actorId, String notes) {
        ItemStatusHistory history = new ItemStatusHistory();
        history.setOrderItem(item);
        history.setStatus(status.toString());
        history.setTimestamp(LocalDateTime.now());
        history.setUpdatedBy(actorId);
        history.setNotes(notes);
        item.getStatusHistory().add(history);
    }

    @Transactional
    public WorkerItemDTO startDelivery(UUID businessId, UUID workerId, UUID orderId) {
        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        if (!"DELIVERY".equals(role)) {
            throw new BusinessException(
                "Delivery operations are only available for the Delivery role.",
                "NOT_DELIVERY_ROLE"
            );
        }

        Order order = findOrder(businessId, orderId);

        if (order.getStatus() != Order.OrderStatus.READY_FOR_PICKUP) {
            throw new BusinessException(
                String.format("Cannot start delivery for order in '%s' status. Expected READY_FOR_PICKUP.",
                    order.getStatus()),
                "INVALID_STATUS_TRANSITION"
            );
        }

        applyOrderTransition(order, Order.OrderStatus.OUT_FOR_DELIVERY, workerId,
            "Out for delivery");
        return mapDeliveryOrderDTO(order);
    }

    @Transactional
    public WorkerItemDTO completeDelivery(UUID businessId, UUID workerId, UUID orderId, String notes) {
        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        if (!"DELIVERY".equals(role)) {
            throw new BusinessException(
                "Delivery operations are only available for the Delivery role.",
                "NOT_DELIVERY_ROLE"
            );
        }

        Order order = findOrder(businessId, orderId);

        if (order.getStatus() != Order.OrderStatus.OUT_FOR_DELIVERY) {
            throw new BusinessException(
                String.format("Cannot complete delivery for order in '%s' status. Expected OUT_FOR_DELIVERY.",
                    order.getStatus()),
                "INVALID_STATUS_TRANSITION"
            );
        }

        Order.OrderStatus previousStatus = order.getStatus();
        order.markCompleted();
        // Attribute the delivery for the order activity trail.
        if (order.getDeliveryInfo() != null) {
            order.getDeliveryInfo().setDeliveredBy(workerId);
        }

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus(Order.OrderStatus.COMPLETED.toString());
        timeline.setDescription(String.format("Delivery completed (%s → %s)%s",
            previousStatus, Order.OrderStatus.COMPLETED,
            notes != null && !notes.isBlank() ? ": " + notes : ""));
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(workerId);
        timeline.setUserName(getUserName(workerId));
        order.getTimeline().add(timeline);

        orderRepository.save(order);
        return mapDeliveryOrderDTO(order);
    }

    /**
     * Batch start/complete for item workers. Processes each item independently;
     * failures are reported per-item instead of aborting the whole batch.
     */
    @Transactional
    public List<Map<String, Object>> batchItems(
            UUID businessId, UUID workerId, List<UUID> itemIds, String action) {

        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);

        if (!ITEM_WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                "Batch operations are only available for Washer and Ironer roles.",
                "NOT_ITEM_WORKER_ROLE"
            );
        }
        if (!"START".equals(action) && !"COMPLETE".equals(action)) {
            throw new BusinessException("Action must be START or COMPLETE", "INVALID_ACTION");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("At least one item is required", "INVALID_REQUEST");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (UUID itemId : itemIds) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("itemId", itemId);
            try {
                WorkerItemDTO dto = "START".equals(action)
                        ? startWorkOnItem(businessId, workerId, itemId)
                        : completeWorkOnItem(businessId, workerId, itemId, null);
                entry.put("success", true);
                entry.put("status", dto.getStatus());
            } catch (BusinessException e) {
                entry.put("success", false);
                entry.put("error", e.getMessage());
            }
            results.add(entry);
        }
        return results;
    }

    private Order findOrder(UUID businessId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        if (!businessId.equals(order.getBusinessId())) {
            throw new BusinessException("Order not found in this business", "ORDER_NOT_FOUND");
        }
        return order;
    }

    private void applyOrderTransition(Order order, Order.OrderStatus next, UUID workerId, String description) {
        Order.OrderStatus previousStatus = order.getStatus();
        order.setStatus(next);

        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus(next.toString());
        timeline.setDescription(String.format("%s (%s → %s)", description, previousStatus, next));
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(workerId);
        timeline.setUserName(getUserName(workerId));
        order.getTimeline().add(timeline);

        orderRepository.save(order);
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
                .map(this::mapDeliveryOrderDTO)
                .collect(Collectors.toList());

        return PageResponse.from(new PageImpl<>(dtos, pageable, orders.getTotalElements()));
    }

    private WorkerItemDTO mapDeliveryOrderDTO(Order order) {
        // For delivery, represent the whole order as a single work item
        List<String> actions = order.getStatus() == Order.OrderStatus.READY_FOR_PICKUP
                ? List.of("START")
                : List.of("COMPLETE");
        return WorkerItemDTO.builder()
                .id(order.getId())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .serviceType("Delivery — " + order.getItemCount() + " pieces")
                .quantity(order.getItemCount())
                .status(order.getStatus().toString())
                .priority(order.getPriority().toString())
                .receivedAt(order.getReceivedAt())
                .promisedDate(order.getPromisedDate())
                .availableActions(actions)
                .needsQualityCheck(false)
                .build();
    }

    /**
     * Synchronizes parent order status based on all item statuses.
     *
     * After an item is IRONED and passes QC, it becomes COMPLETED.
     * When ALL items in an order are COMPLETED, the order becomes READY_FOR_PICKUP.
     */
    private void synchronizeOrderStatus(Order order, UUID workerId) {
        Order refreshedOrder = orderRepository.findById(order.getId())
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        String workerName = getUserName(workerId);
        LocalDateTime now = LocalDateTime.now();

        List<OrderItem> items = refreshedOrder.getItems();
        if (items.isEmpty()) return;

        // Check if all items have passed QC and are COMPLETED
        boolean allCompleted = items.stream()
                .allMatch(item -> item.getStatus() == OrderItem.ItemStatus.COMPLETED);

        if (allCompleted && refreshedOrder.getStatus() != Order.OrderStatus.READY_FOR_PICKUP) {
            Order.OrderStatus previousStatus = refreshedOrder.getStatus();
            refreshedOrder.setStatus(Order.OrderStatus.READY_FOR_PICKUP);
            refreshedOrder.setActualReadyAt(now);

            OrderTimeline timeline = new OrderTimeline();
            timeline.setOrder(refreshedOrder);
            timeline.setType("STATUS_CHANGE");
            timeline.setStatus(Order.OrderStatus.READY_FOR_PICKUP.toString());
            timeline.setDescription(
                String.format("All items completed. Order ready for pickup (was: %s)", previousStatus));
            timeline.setTimestamp(now);
            timeline.setUserId(workerId);
            timeline.setUserName(workerName);
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
                timeline.setTimestamp(now);
                timeline.setUserId(workerId);
                timeline.setUserName(workerName);
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

        // Strip the optional "QL-" prefix ONCE (only when present). All stored
        // formats share the same bare form afterwards:
        //   single item  → "A7K3M9X2-5"  (barcode "QL-A7K3M9X2-5")
        //   unit piece   → "FX78GLJ6-01" (barcode "QL-FX78GLJ6-01")
        //   batch base   → "FX78GLJ6"    (barcode "QL-FX78GLJ6", multi-unit items)
        String bare = itemId.startsWith("QL-") ? itemId.substring(3) : itemId;
        if (bare.isEmpty()) {
            throw new BusinessException("Invalid item ID format", "INVALID_ITEM_ID");
        }
        log.debug("Item ID lookup: input='{}', bare='{}'", itemId, bare);

        // 2. Unit barcode: 2-digit suffix distinguishes per-piece barcodes from
        //    single-item checksum barcodes (single digit).
        if (bare.matches("[A-Z2-9]{8}-\\d{2,}")) {
            OrderItemUnit unit = orderItemUnitRepository
                    .findByBusinessIdAndBarcode(businessId, "QL-" + bare)
                    .orElseThrow(() -> new BusinessException("Item ID not found", "ITEM_NOTFOUND"));
            return unit.getOrderItem();
        }

        // 3. Checksum format "A7K3M9X2-5": validate checksum BEFORE hitting DB,
        //    then match either the stored itemNumber (bare) or barcode (QL-prefixed).
        if (bare.matches("[A-Z2-9]{8}-\\d")) {
            if (!IdGenerator.isValidWithChecksum(bare)) {
                throw new BusinessException("Invalid item ID format", "INVALID_ITEM_ID");
            }
            return orderItemRepository.findByBusinessIdAndCode(businessId, bare)
                    .or(() -> orderItemRepository.findByBusinessIdAndCode(businessId, "QL-" + bare))
                    .orElseThrow(() -> new BusinessException("Item ID not found", "ITEM_NOTFOUND"));
        }

        // 4. Bare base without checksum ("A7K3M9X2"): covers manual entry of the
        //    visible base and multi-unit batch bases ("QL-FX78GLJ6").
        if (bare.matches("[A-Z2-9]{6,12}")) {
            return orderItemRepository.findByBusinessIdAndCode(businessId, bare)
                    .or(() -> orderItemRepository.findByBusinessIdAndCode(businessId, "QL-" + bare))
                    .orElseThrow(() -> new BusinessException("Item ID not found", "ITEM_NOTFOUND"));
        }

        throw new BusinessException("Invalid item ID format", "INVALID_ITEM_ID");
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

        String bare = itemId.startsWith("QL-") ? itemId.substring(3) : itemId;
        boolean prefixed = itemId.startsWith("QL-");

        // 2. Recognised scannable/manual formats → trusted input
        if (bare.matches("[A-Z2-9]{8}-\\d{2,}")
                || (bare.matches("[A-Z2-9]{8}-\\d") && IdGenerator.isValidWithChecksum(bare))
                || bare.matches("[A-Z2-9]{6,12}")) {
            return prefixed ? "QR_SCAN" : "MANUAL_ENTRY_VALID";
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
        shiftRepository.findActiveShift(workerId).ifPresent(shift -> {
            shift.bumpActivity();
            shiftRepository.save(shift);
        });
    }

    /**
     * Whether this garment must pass through washing before finishing.
     * Legacy rows / unknown services default to wash-required.
     */
    private static boolean needsWashing(OrderItem item) {
        return item.getRequiresWashing() == null || item.getRequiresWashing();
    }

    /**
     * Resolves the next status a role can move an item to, honoring the
     * per-item pipeline: iron-only / dry-clean items skip washing and go
     * RECEIVED -> IRONING directly in the ironer's hands. Returns null when
     * the role may not act on the item in its current status.
     */
    private OrderItem.ItemStatus resolveNextStatus(String role, OrderItem item) {
        Map<OrderItem.ItemStatus, OrderItem.ItemStatus> transitions =
            ROLE_ITEM_TRANSITIONS.getOrDefault(role, Map.of());
        OrderItem.ItemStatus direct = transitions.get(item.getStatus());

        if (direct != null) {
            // Washers must never pick up garments that skip washing.
            if ("WASHER".equals(role) && item.getStatus() == OrderItem.ItemStatus.RECEIVED && !needsWashing(item)) {
                return null;
            }
            return direct;
        }
        // Express lane: reception-straight-to-iron for iron-only items.
        if ("IRONER".equals(role)
                && item.getStatus() == OrderItem.ItemStatus.RECEIVED
                && !needsWashing(item)) {
            return OrderItem.ItemStatus.IRONING;
        }
        return null;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
    }

    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("User " + userId.toString().substring(0, 8));
    }

    private WorkerItemDTO mapToWorkerItemDTO(OrderItem item, String currentRole) {
        Map<OrderItem.ItemStatus, OrderItem.ItemStatus> transitions =
            ROLE_ITEM_TRANSITIONS.getOrDefault(currentRole, Map.of());

        List<String> availableActions = new ArrayList<>();
        Set<OrderItem.ItemStatus> produced = new HashSet<>(transitions.values());
        if (resolveNextStatus(currentRole, item) != null && !produced.contains(item.getStatus())) {
            availableActions.add("START");
        }

        boolean needsQC = item.getStatus() == OrderItem.ItemStatus.IRONED;

        // Populate per-unit barcodes for multi-quantity items
        List<String> unitBarcodes = null;
        Integer totalUnits = null;
        if (!item.getUnits().isEmpty()) {
            unitBarcodes = item.getUnits().stream()
                    .map(OrderItemUnit::getBarcode)
                    .collect(Collectors.toList());
            totalUnits = unitBarcodes.size();
        }

        return WorkerItemDTO.builder()
                .id(item.getId())
                .shortId(item.getBarcode())
                .orderId(item.getOrder().getId())
                .orderNumber(item.getOrder().getOrderNumber())
                .serviceType(item.getServiceType())
                .garmentType(item.getGarmentType())
                .requiresWashing(needsWashing(item))
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .weight(item.getWeight())
                .status(item.getStatus().toString())
                .specialInstructions(item.getSpecialInstructions())
                .images(item.getImages())
                .priority(item.getOrder().getPriority().toString())
                .receivedAt(item.getOrder().getReceivedAt())
                .promisedDate(item.getOrder().getPromisedDate())
                .unitBarcodes(unitBarcodes)
                .totalUnits(totalUnits)
                .availableActions(availableActions)
                .needsQualityCheck(needsQC)
                .build();
    }
}