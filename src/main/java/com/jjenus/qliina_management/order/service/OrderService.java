package com.jjenus.qliina_management.order.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.util.IdGenerator;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.customer.service.CustomerService;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.model.Shop;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.dto.*;
import com.jjenus.qliina_management.order.dto.ReturnOrderRequest;
import com.jjenus.qliina_management.order.model.*;
import com.jjenus.qliina_management.order.repository.OrderItemUnitRepository;
import com.jjenus.qliina_management.order.repository.OrderItemRepository;
import com.jjenus.qliina_management.order.repository.ItemWorkerInteractionRepository;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.order.repository.OrderSpecifications;
import com.jjenus.qliina_management.payment.service.PaymentService;
import com.jjenus.qliina_management.quality.model.QualityCheck;
import com.jjenus.qliina_management.quality.repository.QualityCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.business.service.PlanLimitService;
import com.jjenus.qliina_management.business.model.ServiceType;
import com.jjenus.qliina_management.business.model.GarmentType;
import com.jjenus.qliina_management.business.repository.ServiceTypeRepository;
import com.jjenus.qliina_management.business.repository.GarmentTypeRepository;
import com.jjenus.qliina_management.common.websocket.WebSocketPublisher;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final QualityCheckRepository qualityCheckRepository;
    private final PlanLimitService planLimitService;
    private final ServiceTypeRepository serviceTypeRepository;
    private final GarmentTypeRepository garmentTypeRepository;
    private final ItemWorkerInteractionRepository interactionRepository;

    private UUID getCurrentUserId() {
        return SecurityContextUtil.getCurrentUserId().orElse(null);
    }
    
    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("User " + userId.toString().substring(0, 8));
    }

    private void recordItemInteraction(UUID itemId, UUID userId, String accessMethod) {
        if (userId == null) return;
        var existing = interactionRepository.findByWorkerIdAndItemId(userId, itemId);
        if (existing.isPresent()) {
            ItemWorkerInteraction iwi = existing.get();
            iwi.recordInteraction();
            interactionRepository.save(iwi);
        } else {
            ItemWorkerInteraction iwi = new ItemWorkerInteraction();
            iwi.setWorkerId(userId);
            iwi.setItemId(itemId);
            iwi.setFirstInteraction(LocalDateTime.now());
            iwi.setLastInteraction(LocalDateTime.now());
            iwi.setInteractionCount(1);
            iwi.setFirstAccessMethod(accessMethod);
            interactionRepository.save(iwi);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDTO> listOrders(UUID businessId, OrderFilter filter, Pageable pageable) {
        var specification = OrderSpecifications.withFilter(businessId, filter);
        Page<Order> page = orderRepository.findAll(specification, pageable);
        return PageResponse.from(page.map(this::mapToSummaryDTO));
    }
    
    @Transactional(readOnly = true)
    public OrderDetailDTO getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        return mapToDetailDTO(order);
    }
    
    public Long countPendingOrders(UUID businessId, UUID shopId) {
    return orderRepository.countPendingOrders(businessId, shopId);
}

public Long countOrdersByDateRange(UUID businessId, UUID shopId, LocalDateTime startDate, LocalDateTime endDate) {
    return orderRepository.countOrdersByDateRange(businessId, shopId, startDate, endDate);
}
    
    @Transactional(readOnly = true)
    public OrderDetailDTO getOrderByTrackingNumber(String trackingNumber) {
        Order order = orderRepository.findByTrackingNumber(trackingNumber)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        return mapToDetailDTO(order);
    }
    
    @Transactional
    public OrderDetailDTO createOrder(UUID businessId, CreateOrderRequest request) {
        planLimitService.enforceOrderLimit(businessId);
        Order order = new Order();
        order.setBusinessId(businessId);
        order.setShopId(request.getShopId());
        order.setCustomerId(request.getCustomerId());
        order.setOrderNumber(IdGenerator.generateOrderId());
        order.setTrackingNumber(IdGenerator.generateTrackingId());
        order.setStatus(Order.OrderStatus.RECEIVED);
        order.setPriority(request.getPriority() != null ? 
            Order.Priority.valueOf(request.getPriority()) : Order.Priority.NORMAL);
        order.setReceivedAt(LocalDateTime.now());
        
        if (request.getPromisedDate() != null) {
            order.setPromisedDate(request.getPromisedDate());
        }
        
        if (request.getExpectedReadyAt() != null) {
            order.setExpectedReadyAt(request.getExpectedReadyAt());
        }
        
        if (request.getTags() != null) {
            order.setTags(new HashSet<>(request.getTags()));
        }
        
        // Create order items
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int itemCount = 0;
        
        for (CreateOrderRequest.ItemDTO itemDto : request.getItems()) {
            OrderItem item = new OrderItem();
            String itemTrackingNumber = IdGenerator.generateQrCode("item");
            
            item.setOrder(order);
            item.setItemNumber(itemTrackingNumber.substring(3));
            item.setBarcode(itemTrackingNumber);
            // Resolve catalog UUIDs to human-readable names so the UI never
            // shows raw UUIDs in the service-type / garment-type columns.
            String resolvedServiceType = serviceTypeRepository.findById(itemDto.getServiceTypeId())
                    .map(ServiceType::getName)
                    .orElse(itemDto.getServiceTypeId().toString());
            item.setServiceType(resolvedServiceType);

            String resolvedGarmentType = (itemDto.getGarmentTypeId() != null)
                    ? garmentTypeRepository.findById(itemDto.getGarmentTypeId())
                            .map(GarmentType::getName)
                            .orElse(itemDto.getGarmentTypeId().toString())
                    : null;
            item.setGarmentType(resolvedGarmentType);
            item.setDescription(itemDto.getDescription());
            item.setQuantity(itemDto.getQuantity());
            item.setWeight(itemDto.getWeight() != null ? BigDecimal.valueOf(itemDto.getWeight()) : null);
            item.setUnitPrice(BigDecimal.valueOf(itemDto.getUnitPrice()));
            
            BigDecimal subtotal;
            if (itemDto.getWeight() != null) {
                subtotal = BigDecimal.valueOf(itemDto.getUnitPrice() * itemDto.getWeight());
            } else {
                subtotal = BigDecimal.valueOf(itemDto.getUnitPrice() * itemDto.getQuantity());
            }
            
            item.setSubtotal(subtotal);
            item.setDiscount(BigDecimal.ZERO);
            item.setTotal(subtotal);
            item.setStatus(OrderItem.ItemStatus.RECEIVED);
            item.setSpecialInstructions(itemDto.getSpecialInstructions());
            
            if (itemDto.getImages() != null) {
                item.setImages(new ArrayList<>(itemDto.getImages()));
            }
            
            // For multi-quantity items, generate per-unit barcodes so each
            // physical piece gets its own scannable ID (e.g. "QL-FX78GLJ6-01").
            if (itemDto.getQuantity() > 1) {
                String batchBase = IdGenerator.generateBatchBase();
                List<OrderItemUnit> units = new ArrayList<>();
                for (int n = 1; n <= itemDto.getQuantity(); n++) {
                    OrderItemUnit unit = new OrderItemUnit();
                    unit.setOrderItem(item);
                    unit.setUnitNumber(n);
                    unit.setBarcode(IdGenerator.generateUnitBarcode(batchBase, n));
                    units.add(unit);
                }
                item.setUnits(units);
                // Override the item-level barcode/itemNumber to use the batch base
                item.setBarcode("QL-" + batchBase);
                item.setItemNumber(batchBase);
            }

            items.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setItems(items);
        // itemCount stores total individual pieces (sum of quantities),
        // not the number of line-item rows, so "3 shirts" counts as 3.
        int totalQuantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        order.setItemCount(totalQuantity);
        
        // Apply discounts
        if (request.getDiscounts() != null) {
            for (CreateOrderRequest.DiscountDTO discountDto : request.getDiscounts()) {
                if ("PERCENTAGE".equals(discountDto.getType())) {
                    BigDecimal discountAmount = totalAmount.multiply(
                        BigDecimal.valueOf(discountDto.getValue()).divide(BigDecimal.valueOf(100)));
                    totalAmount = totalAmount.subtract(discountAmount);
                } else {
                    totalAmount = totalAmount.subtract(BigDecimal.valueOf(discountDto.getValue()));
                }
            }
        }
        
        order.setTotalAmount(totalAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setBalanceDue(totalAmount);
        
        // Set delivery info
        if (request.getDelivery() != null) {
            DeliveryInfo deliveryInfo = new DeliveryInfo();
            deliveryInfo.setType(request.getDelivery().getType());
            deliveryInfo.setAddressId(request.getDelivery().getAddressId());
            deliveryInfo.setScheduledTime(request.getDelivery().getScheduledTime());
            order.setDeliveryInfo(deliveryInfo);
        }
        
        // Create timeline entry
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus("RECEIVED");
        timeline.setDescription("Order received");
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(getCurrentUserId());
        timeline.setUserName(getUserName(getCurrentUserId()));
        order.getTimeline().add(timeline);
        
        // Add note if provided
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            OrderNote note = new OrderNote();
            note.setOrder(order);
            note.setContent(request.getNotes());
            note.setType("GENERAL");
            note.setIsCustomerVisible(true);
            order.getNotes().add(note);
        }
        
        order = orderRepository.save(order);
        
        // Update customer metrics
        customerService.updateCustomerFromOrder(request.getCustomerId(), totalAmount);
        
        return mapToDetailDTO(order);
    }
    
    @Transactional
    public OrderDetailDTO quickOrder(UUID businessId, QuickOrderRequest request) {
        // Find or create customer by phone
        Customer customer = customerRepository.findByBusinessIdAndPhone(businessId, request.getCustomerPhone())
            .orElseGet(() -> {
                Customer newCustomer = new Customer();
                newCustomer.setBusinessId(businessId);
                newCustomer.setFirstName("Guest");
                newCustomer.setLastName("Customer");
                newCustomer.setPhone(request.getCustomerPhone());
                newCustomer.setEnabled(true);
                return customerRepository.save(newCustomer);
            });
        
        CreateOrderRequest createRequest = new CreateOrderRequest();
        createRequest.setShopId(request.getShopId());
        createRequest.setCustomerId(customer.getId());
        createRequest.setItems(List.of(
            CreateOrderRequest.ItemDTO.builder()
                .serviceTypeId(UUID.nameUUIDFromBytes(request.getServiceType().getBytes()))
                .quantity(request.getItemCount())
                .unitPrice(request.getTotalAmount() / request.getItemCount())
                .build()
        ));
        createRequest.setPriority("NORMAL");
        createRequest.setNotes(request.getNotes());
        
        return createOrder(businessId, createRequest);
    }
    
    @Transactional
    public OrderDetailDTO updateOrder(UUID orderId, UpdateOrderRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        if (request.getPriority() != null) {
            order.setPriority(Order.Priority.valueOf(request.getPriority()));
        }
        
        if (request.getPromisedDate() != null) {
            order.setPromisedDate(request.getPromisedDate());
        }
        
        if (request.getTags() != null) {
            order.setTags(new HashSet<>(request.getTags()));
        }
        
        if (request.getDelivery() != null) {
            DeliveryInfo deliveryInfo = order.getDeliveryInfo();
            if (deliveryInfo == null) {
                deliveryInfo = new DeliveryInfo();
            }
            deliveryInfo.setType(request.getDelivery().getType());
            deliveryInfo.setAddressId(request.getDelivery().getAddressId());
            deliveryInfo.setScheduledTime(request.getDelivery().getScheduledTime());
            order.setDeliveryInfo(deliveryInfo);
        }
        
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            OrderNote note = new OrderNote();
            note.setOrder(order);
            note.setContent(request.getNotes());
            note.setType("GENERAL");
            note.setIsCustomerVisible(true);
            order.getNotes().add(note);
        }
        
        order = orderRepository.save(order);
        return mapToDetailDTO(order);
    }
    
    @Transactional
    public void cancelOrder(UUID orderId, CancelOrderRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel completed order", "ORDER_ALREADY_COMPLETED");
        }
        
        order.setStatus(Order.OrderStatus.CANCELLED);
        
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus("CANCELLED");
        timeline.setDescription(request.getReason());
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(getCurrentUserId());
        timeline.setUserName(getUserName(getCurrentUserId()));
        timeline.setMetadata(Map.of("reason", request.getReason(), "notes", request.getNotes()));
        order.getTimeline().add(timeline);
        
        orderRepository.save(order);
    }
    

    /**
     * Updates the order status and cascades the status change to all items
     * that haven't progressed past the new status.
     * 
     * Records full audit trail:
     * - OrderTimeline entry (who changed order status, when)
     * - ItemStatusHistory entries for each affected item
     * 
     * Validates that the status transition is allowed before proceeding.
     */
    @Transactional
    public OrderStatusDTO updateOrderStatus(UUID orderId, UpdateStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
    
        Order.OrderStatus previousStatus = order.getStatus();
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(request.getStatus());
    
        // Validate status transition
        validateStatusTransition(previousStatus, newStatus);

        // Block processing-worker roles from order-level status changes.
        // WASHER / IRONER / DELIVERY must use WorkerOrderController item endpoints.
        UUID callerId = SecurityContextUtil.requireUserId();
        userRepository.findById(callerId).ifPresent(caller ->
            caller.getRoles().stream()
                .map(ur -> ur.getRole().getName())
                .filter(name -> Set.of("WASHER", "IRONER", "DELIVERY").contains(name))
                .findFirst()
                .ifPresent(role -> { throw new BusinessException(
                    "Workers cannot perform order-level status changes. Use the work-queue.",
                    "INSUFFICIENT_ROLE"); })
        );

        // Update order status
        order.setStatus(newStatus);
    
        if (newStatus == Order.OrderStatus.READY_FOR_PICKUP) {
            order.setActualReadyAt(LocalDateTime.now());
        } else if (newStatus == Order.OrderStatus.COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
        }
    
        String userName = SecurityContextUtil.requireUsername();
        UUID currentUserId = getCurrentUserId();
    
        // CASCADE: Update all eligible items to match the new order status.
        // This ensures items progress with the order when a manager updates it.
        // Items that have already progressed further are NOT rolled back.

        OrderItem.ItemStatus mappedItemStatus = mapOrderStatusToItemStatus(newStatus);
        if (mappedItemStatus != null) {
            for (OrderItem item : order.getItems()) {
                if (shouldUpdateItemStatus(item.getStatus(), mappedItemStatus)) {
                    OrderItem.ItemStatus previousItemStatus = item.getStatus();
                    item.setStatus(mappedItemStatus);
    
                    // Record item-level audit history
                    ItemStatusHistory itemHistory = new ItemStatusHistory();
                    itemHistory.setOrderItem(item);
                    itemHistory.setStatus(mappedItemStatus.toString());
                    itemHistory.setTimestamp(LocalDateTime.now());
                    itemHistory.setUpdatedBy(currentUserId);
                    itemHistory.setNotes(String.format(
                        "Status cascaded from order (%s → %s) by %s",
                        previousItemStatus, mappedItemStatus, userName));
                    item.getStatusHistory().add(itemHistory);

                    // Record interaction attribution (who did it)
                    recordItemInteraction(item.getId(), currentUserId, "ORDER_CASCADE");
                }
            }
        }
    
        // Record order-level timeline
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus(newStatus.toString());
        timeline.setDescription(request.getNotes());
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(currentUserId);
        timeline.setUserName(getUserName(currentUserId));
        order.getTimeline().add(timeline);
    
        order = orderRepository.save(order);
    
        // Broadcast real-time update
        webSocketPublisher.publishOrderUpdate(
            order.getBusinessId(), order.getId(),
            OrderStatusDTO.builder()
                .orderId(order.getId())
                .previousStatus(previousStatus.toString())
                .currentStatus(newStatus.toString())
                .updatedAt(LocalDateTime.now())
                .build());
    
        return OrderStatusDTO.builder()
                .orderId(order.getId())
                .previousStatus(previousStatus.toString())
                .currentStatus(newStatus.toString())
                .updatedAt(LocalDateTime.now())
                .updatedBy(userName)
                .notes(request.getNotes())
                .estimatedCompletion(order.getExpectedReadyAt())
                .build();
    }
        
    @Transactional
    public OrderItemStatusDTO updateItemStatus(UUID orderId, UUID itemId, UpdateItemStatusRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        OrderItem item = order.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new BusinessException("Order item not found", "ITEM_NOT_FOUND"));
        
        OrderItem.ItemStatus previousStatus = item.getStatus();
        OrderItem.ItemStatus newStatus = OrderItem.ItemStatus.valueOf(request.getStatus());

        // Validate forward-only progression (same as cascade logic)
        if (!shouldUpdateItemStatus(previousStatus, newStatus)) {
            throw new BusinessException(
                String.format("Invalid item status transition from %s to %s. Items can only move forward.", previousStatus, newStatus),
                "INVALID_ITEM_STATUS_TRANSITION");
        }
        
        item.setStatus(newStatus);
        
        ItemStatusHistory history = new ItemStatusHistory();
        history.setOrderItem(item);
        history.setStatus(newStatus.toString());
        history.setTimestamp(LocalDateTime.now());
        history.setUpdatedBy(getCurrentUserId());
        history.setNotes(request.getNotes());
        item.getStatusHistory().add(history);
        recordItemInteraction(item.getId(), getCurrentUserId(), "ITEM_STATUS_UPDATE");
        
        orderRepository.save(order);
        
        return OrderItemStatusDTO.builder()
            .itemId(itemId)
            .previousStatus(previousStatus.toString())
            .currentStatus(newStatus.toString())
            .updatedAt(LocalDateTime.now())
            .updatedBy(getUserName(getCurrentUserId()))
            .notes(request.getNotes())
            .build();
    }
    
    @Transactional(readOnly = true)
    public List<TimelineEventDTO> getOrderTimeline(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        return order.getTimeline().stream()
            .map(event -> TimelineEventDTO.builder()
                .id(event.getId())
                .type(event.getType())
                .status(event.getStatus())
                .description(event.getDescription())
                .timestamp(event.getTimestamp())
                .userId(event.getUserId())
                .userName(event.getUserName())
                .metadata(event.getMetadata())
                .build())
            .collect(Collectors.toList());
    }
    
    @Transactional
    public OrderNoteDTO addOrderNote(UUID orderId, AddNoteRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        OrderNote note = new OrderNote();
        note.setOrder(order);
        note.setContent(request.getContent());
        note.setType(request.getType());
        note.setIsCustomerVisible(request.getIsCustomerVisible() != null ? request.getIsCustomerVisible() : false);
        
        order.getNotes().add(note);
        orderRepository.save(order);
        
        return OrderNoteDTO.builder()
            .id(note.getId())
            .content(note.getContent())
            .type(note.getType())
            .createdBy(getUserName(getCurrentUserId()))
            .createdAt(note.getCreatedAt())
            .isCustomerVisible(note.getIsCustomerVisible())
            .attachments(request.getAttachments())
            .build();
    }
    
    /**
     * Processes a customer return on a COMPLETED (or OUT_FOR_DELIVERY) order.
     * <p>
     * Transitions the order to RETURNED, marks the specified items (or all items)
     * as ISSUE_REPORTED, records an order note with the customer's reason, and adds
     * a RETURN_INITIATED timeline event. If a refund is requested, that is noted in the
     * timeline for finance review. The manager then re-runs quality check via the normal
     * RETURNED → QUALITY_CHECK transition.
     */
    @Transactional
    public OrderDetailDTO returnOrder(UUID businessId, UUID orderId, ReturnOrderRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));

        if (order.getStatus() != Order.OrderStatus.COMPLETED
                && order.getStatus() != Order.OrderStatus.OUT_FOR_DELIVERY) {
            throw new BusinessException(
                    "Only COMPLETED or OUT_FOR_DELIVERY orders can be returned",
                    "INVALID_ORDER_STATE");
        }

        UUID currentUserId = getCurrentUserId();
        String userName = getUserName(currentUserId);
        LocalDateTime now = LocalDateTime.now();

        // Capture previous status before transition so the WebSocket broadcast
        // can carry both previousStatus and currentStatus (matches OrderStatusDTO contract).
        Order.OrderStatus previousStatus = order.getStatus();

        // Transition order to RETURNED
        order.setStatus(Order.OrderStatus.RETURNED);
        order.setCompletedAt(null); // no longer complete

        // Determine which items are being returned
        List<OrderItem> returnedItems;
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            returnedItems = order.getItems();
        } else {
            Set<UUID> returnIds = new HashSet<>(request.getItemIds());
            returnedItems = order.getItems().stream()
                    .filter(i -> returnIds.contains(i.getId()))
                    .collect(Collectors.toList());
        }

        // Mark each returned item as ISSUE_REPORTED and record history
        for (OrderItem item : returnedItems) {
            item.setStatus(OrderItem.ItemStatus.ISSUE_REPORTED);
            ItemStatusHistory itemHistory = new ItemStatusHistory();
            itemHistory.setOrderItem(item);
            itemHistory.setStatus(OrderItem.ItemStatus.ISSUE_REPORTED.toString());
            itemHistory.setTimestamp(now);
            itemHistory.setUpdatedBy(currentUserId);
            itemHistory.setNotes("Customer return: " + request.getReason());
            item.getStatusHistory().add(itemHistory);
            recordItemInteraction(item.getId(), currentUserId, "CUSTOMER_RETURN");
        }

        // Add customer-visible note with the return reason
        OrderNote note = new OrderNote();
        note.setOrder(order);
        note.setContent("Customer return — " + request.getReason()
                + (request.getCustomerNotes() != null ? "\nCustomer notes: " + request.getCustomerNotes() : ""));
        note.setType("RETURN");
        note.setIsCustomerVisible(true);
        order.getNotes().add(note);

        // Timeline event
        String timelineDesc = "Return initiated by " + userName + ". Reason: " + request.getReason()
                + (request.isRefundRequested() ? " [REFUND REQUESTED — pending finance review]" : "");
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("RETURN_INITIATED");
        timeline.setStatus(Order.OrderStatus.RETURNED.toString());
        timeline.setDescription(timelineDesc);
        timeline.setTimestamp(now);
        timeline.setUserId(currentUserId);
        timeline.setUserName(userName);
        order.getTimeline().add(timeline);

        order = orderRepository.save(order);

        // Broadcast real-time update — payload matches OrderStatusDTO contract
        // used by updateOrderStatus() and expected by the frontend's OrderStatusDTO type.
        webSocketPublisher.publishOrderUpdate(
                order.getBusinessId(), order.getId(),
                OrderStatusDTO.builder()
                        .orderId(order.getId())
                        .previousStatus(previousStatus.toString())
                        .currentStatus(order.getStatus().toString())
                        .updatedAt(now)
                        .updatedBy(userName)
                        .build());

        return mapToDetailDTO(order);
    }

    @Transactional(readOnly = true)
    public DailyOrdersSummaryDTO getDailyOrderSummary(UUID businessId, LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        
        List<Order> orders = orderRepository.findByDateRange(businessId, startOfDay, endOfDay, Pageable.unpaged())
            .getContent();
        
        long totalOrders = orders.size();
        long completedOrders = orders.stream()
            .filter(o -> o.getStatus() == Order.OrderStatus.COMPLETED)
            .count();
        long pendingOrders = orders.stream()
            .filter(o -> o.getStatus() != Order.OrderStatus.COMPLETED && 
                        o.getStatus() != Order.OrderStatus.ARCHIVED)
            .count();
        
        BigDecimal totalRevenue = orders.stream()
            .map(Order::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgOrderValue = totalOrders > 0 ? 
            totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        Map<String, ServiceSummary> byServiceType = new HashMap<>();
        Map<Integer, Integer> byHour = new HashMap<>();
        
        for (Order order : orders) {
            int hour = order.getCreatedAt().getHour();
            byHour.put(hour, byHour.getOrDefault(hour, 0) + 1);
            
            for (OrderItem item : order.getItems()) {
                String serviceType = item.getServiceType();
                ServiceSummary summary = byServiceType.getOrDefault(serviceType, 
                    new ServiceSummary(serviceType, 0, BigDecimal.ZERO));
                summary.setCount(summary.getCount() + 1);
                summary.setRevenue(summary.getRevenue().add(item.getTotal()));
                byServiceType.put(serviceType, summary);
            }
        }
        
        int peakHour = byHour.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(0);
        
        return DailyOrdersSummaryDTO.builder()
            .date(date.toLocalDate())
            .totalOrders((int) totalOrders)
            .completedOrders((int) completedOrders)
            .pendingOrders((int) pendingOrders)
            .totalRevenue(totalRevenue)
            .averageOrderValue(avgOrderValue)
            .byServiceType(byServiceType.values().stream()
                .map(s -> DailyOrdersSummaryDTO.ServiceSummaryDTO.builder()
                    .serviceType(s.getServiceType())
                    .count(s.getCount())
                    .revenue(s.getRevenue())
                    .build())
                .collect(Collectors.toList()))
            .byHour(byHour.entrySet().stream()
                .map(e -> DailyOrdersSummaryDTO.HourlyDTO.builder()
                    .hour(e.getKey())
                    .count(e.getValue())
                    .build())
                .sorted((a, b) -> Integer.compare(a.getHour(), b.getHour()))
                .collect(Collectors.toList()))
            .peakHour(peakHour)
            .build();
    }
    
    @Transactional
    public OrderDetailDTO transferOrder(UUID orderId, TransferOrderRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        UUID previousShopId = order.getShopId();
        order.setShopId(request.getTargetShopId());
        
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("TRANSFER");
        timeline.setDescription(String.format("Order transferred from shop %s to %s. Reason: %s", 
            previousShopId, request.getTargetShopId(), request.getReason()));
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(getCurrentUserId());
        timeline.setUserName(getUserName(getCurrentUserId()));
        timeline.setMetadata(Map.of(
            "fromShop", previousShopId.toString(),
            "toShop", request.getTargetShopId().toString(),
            "reason", request.getReason()
        ));
        order.getTimeline().add(timeline);
        
        if (request.getTransferNotes() != null) {
            OrderNote note = new OrderNote();
            note.setOrder(order);
            note.setContent(request.getTransferNotes());
            note.setType("TRANSFER");
            note.setIsCustomerVisible(false);
            order.getNotes().add(note);
        }
        
        order = orderRepository.save(order);
        return mapToDetailDTO(order);
    }
    
    // ── Public order tracking ──────────────────────────────────────────────────

    /**
     * Returns a minimal, privacy-safe order summary for unauthenticated
     * customer-facing tracking pages. No prices, no full customer details.
     */
    public PublicOrderTrackDTO getPublicTrackingInfo(String trackingNumber) {
        Order order = orderRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));

        List<PublicOrderTrackDTO.ItemRow> itemRows = order.getItems().stream()
                .map(i -> PublicOrderTrackDTO.ItemRow.builder()
                        .serviceType(i.getServiceType())
                        .garmentType(i.getGarmentType())
                        .quantity(i.getQuantity())
                        .status(i.getStatus().toString())
                        .build())
                .collect(Collectors.toList());

        List<PublicOrderTrackDTO.TimelineRow> timelineRows = order.getTimeline().stream()
                .sorted(Comparator.comparing(OrderTimeline::getTimestamp).reversed())
                .limit(5)
                .map(t -> PublicOrderTrackDTO.TimelineRow.builder()
                        .status(t.getStatus())
                        .description(t.getDescription())
                        .timestamp(t.getTimestamp())
                        .build())
                .collect(Collectors.toList());

        // Resolve shop and business names safely (null-guarded)
        String shopName = null;
        String businessName = null;
        if (order.getShopId() != null) {
            shopName = shopRepository.findById(order.getShopId())
                    .map(Shop::getName).orElse(null);
        }
        if (order.getBusinessId() != null) {
            businessName = businessRepository.findById(order.getBusinessId())
                    .map(Business::getName).orElse(null);
        }

        // Customer first name only — enough for personalisation without exposing PII
        String customerFirstName = null;
        if (order.getCustomerId() != null) {
            customerFirstName = customerRepository.findById(order.getCustomerId())
                    .map(c -> c.getFirstName()).orElse(null);
        }

        return PublicOrderTrackDTO.builder()
                .orderNumber(order.getOrderNumber())
                .trackingNumber(order.getTrackingNumber())
                .status(order.getStatus().toString())
                .priority(order.getPriority().toString())
                .shopName(shopName)
                .businessName(businessName)
                .customerFirstName(customerFirstName)
                .receivedAt(order.getReceivedAt())
                .promisedDate(order.getPromisedDate())
                .completedAt(order.getCompletedAt())
                .items(itemRows)
                .recentTimeline(timelineRows)
                .build();
    }

    private void validateStatusTransition(Order.OrderStatus from, Order.OrderStatus to) {
        // Define allowed transitions
        Map<Order.OrderStatus, List<Order.OrderStatus>> allowedTransitions = Map.ofEntries(
            Map.entry(Order.OrderStatus.DRAFT, List.of(Order.OrderStatus.RECEIVED)),
            Map.entry(Order.OrderStatus.RECEIVED, List.of(Order.OrderStatus.WASHING, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.WASHING, List.of(Order.OrderStatus.WASHED, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.WASHED, List.of(Order.OrderStatus.IRONING, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.IRONING, List.of(Order.OrderStatus.IRONED, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.IRONED, List.of(Order.OrderStatus.QUALITY_CHECK, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.QUALITY_CHECK, List.of(Order.OrderStatus.READY_FOR_PICKUP, Order.OrderStatus.WASHING, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.READY_FOR_PICKUP, List.of(Order.OrderStatus.OUT_FOR_DELIVERY, Order.OrderStatus.COMPLETED, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.OUT_FOR_DELIVERY, List.of(Order.OrderStatus.COMPLETED, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.COMPLETED, List.of(Order.OrderStatus.RETURNED, Order.OrderStatus.ARCHIVED)),
            Map.entry(Order.OrderStatus.RETURNED, List.of(Order.OrderStatus.QUALITY_CHECK, Order.OrderStatus.COMPLETED, Order.OrderStatus.ARCHIVED))
        );
        
        List<Order.OrderStatus> allowed = allowedTransitions.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(
                String.format("Invalid status transition from %s to %s", from, to),
                "INVALID_STATUS_TRANSITION"
            );
        }
    }
    

    
    private String generateTrackingNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.format("%06d", new Random().nextInt(1000000));
        return "TRK" + timestamp.substring(timestamp.length() - 8) + random;
    }
    
    /**
     * Maps order-level status to corresponding item-level status.
     * Only maps to item statuses that make sense for items to inherit.
     * Delivery-related statuses (OUT_FOR_DELIVERY) don't map to items.
     */
    private OrderItem.ItemStatus mapOrderStatusToItemStatus(Order.OrderStatus orderStatus) {
        return switch (orderStatus) {
            case RECEIVED -> OrderItem.ItemStatus.RECEIVED;
            case WASHING -> OrderItem.ItemStatus.WASHING;
            case WASHED -> OrderItem.ItemStatus.WASHED;
            case IRONING -> OrderItem.ItemStatus.IRONING;
            case IRONED -> OrderItem.ItemStatus.IRONED;
            case QUALITY_CHECK -> OrderItem.ItemStatus.QUALITY_CHECK;
            case READY_FOR_PICKUP -> OrderItem.ItemStatus.COMPLETED;
            // These order statuses don't have item-level equivalents.
            // RETURNED items are individually marked ISSUE_REPORTED by returnOrder().
            case OUT_FOR_DELIVERY, COMPLETED, RETURNED, CANCELLED, ARCHIVED, DRAFT -> null;
        };
    }
    
    /**
     * Determines if an item should be updated to the target status.
     * Uses ordinal comparison: items only move forward, never backward.
     * Items already at or past the target status are left alone.
     */
    private boolean shouldUpdateItemStatus(OrderItem.ItemStatus current, OrderItem.ItemStatus target) {
        return getItemStatusOrdinal(current) < getItemStatusOrdinal(target);
    }
    
    /**
     * Returns the workflow position of an item status.
     * Higher numbers = further along in the laundry process.
     * PENDING(0) → RECEIVED(1) → WASHING(2) → ... → COMPLETED(7)
     */
    private int getItemStatusOrdinal(OrderItem.ItemStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case RECEIVED -> 1;
            case WASHING -> 2;
            case WASHED -> 3;
            case IRONING -> 4;
            case IRONED -> 5;
            case QUALITY_CHECK -> 6;
            case COMPLETED -> 7;
            case ISSUE_REPORTED -> 99; // Issues are tracked separately, don't participate in flow
        };
    }
    
    private String generateItemNumber(String orderNumber, int sequence) {
        return orderNumber + "-ITM-" + String.format("%03d", sequence);
    }
    
    private OrderSummaryDTO mapToSummaryDTO(Order order) {
        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        Shop shop = shopRepository.findById(order.getShopId()).orElse(null);
        
        return OrderSummaryDTO.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .trackingNumber(order.getTrackingNumber())
            .customer(OrderSummaryDTO.CustomerInfo.builder()
                .id(order.getCustomerId())
                .name(customer != null ? customer.getFirstName() + " " + customer.getLastName() : "Unknown")
                .phone(customer != null ? customer.getPhone() : "")
                .build())
            .shop(OrderSummaryDTO.ShopInfo.builder()
                .id(order.getShopId())
                .name(shop != null ? shop.getName() : "Unknown")
                .build())
            .status(order.getStatus().toString())
            .itemCount(order.getItemCount())
            .totalAmount(order.getTotalAmount())
            .paidAmount(order.getPaidAmount())
            .balanceDue(order.getBalanceDue())
            .priority(order.getPriority().toString())
            .promisedDate(order.getPromisedDate())
            .receivedAt(order.getReceivedAt())
            .expectedReadyAt(order.getExpectedReadyAt())
            .actualReadyAt(order.getActualReadyAt())
            .completedAt(order.getCompletedAt())
            .createdBy(getUserName(order.getCreatedBy()))
            .createdAt(order.getCreatedAt())
            .tags(new ArrayList<>(order.getTags()))
            .build();
    }
    
    private OrderDetailDTO mapToDetailDTO(Order order) {
        OrderSummaryDTO summary = mapToSummaryDTO(order);
        
        List<OrderDetailDTO.ItemDTO> itemDTOs = order.getItems().stream()
            .map(item -> {
                List<OrderDetailDTO.ItemStatusHistoryDTO> historyDTOs = item.getStatusHistory().stream()
                    .map(h -> OrderDetailDTO.ItemStatusHistoryDTO.builder()
                        .status(h.getStatus())
                        .timestamp(h.getTimestamp())
                        .updatedBy(getUserName(h.getUpdatedBy()))
                        .notes(h.getNotes())
                        .build())
                    .collect(Collectors.toList());
                
                // FIXED: Fetch QualityCheck separately using repository
                OrderDetailDTO.QualityCheckDTO qualityCheckDTO = null;
                Optional<QualityCheck> qualityCheckOpt = qualityCheckRepository.findByOrderItemId(item.getId());
                if (qualityCheckOpt.isPresent()) {
                    QualityCheck qualityCheck = qualityCheckOpt.get();
                    qualityCheckDTO = OrderDetailDTO.QualityCheckDTO.builder()
                        .status(qualityCheck.getStatus())
                        .checkedBy(getUserName(qualityCheck.getCheckedBy()))
                        .checkedAt(qualityCheck.getCheckedAt())
                        .defects(qualityCheck.getDefects().stream()
                            .map(d -> OrderDetailDTO.DefectDTO.builder()
                                .id(d.getId())
                                .type(d.getType())
                                .severity(d.getSeverity())
                                .description(d.getDescription())
                                .images(d.getImages())
                                .reportedBy(getUserName(d.getReportedBy()))
                                .reportedAt(d.getReportedAt())
                                .status(d.getStatus())
                                .resolution(d.getResolution())
                                .compensation(d.getCompensation())
                                .compensationType(d.getCompensationType())
                                .build())
                            .collect(Collectors.toList()))
                        .notes(qualityCheck.getNotes())
                        .build();
                }
                
                return OrderDetailDTO.ItemDTO.builder()
                    .id(item.getId())
                    .itemNumber(item.getItemNumber())
                    .barcode(item.getBarcode())
                    .serviceType(item.getServiceType())
                    .garmentType(item.getGarmentType())
                    .description(item.getDescription())
                    .quantity(item.getQuantity())
                    .weight(item.getWeight())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(item.getSubtotal())
                    .discount(item.getDiscount())
                    .total(item.getTotal())
                    .status(item.getStatus().toString())
                    .specialInstructions(item.getSpecialInstructions())
                    .images(item.getImages())
                    .statusHistory(historyDTOs)
                    .qualityCheck(qualityCheckDTO)
                    .build();
            })
            .collect(Collectors.toList());
        
        List<OrderDetailDTO.PaymentDTO> paymentDTOs = paymentRepository.findByOrderId(order.getId())
            .stream()
            .map(p -> OrderDetailDTO.PaymentDTO.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .method(p.getMethod())
                .reference(p.getReference())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .collectedBy(getUserName(p.getCollectedBy()))
                .tip(p.getTip())
                .change(p.getChangeAmount())
                .metadata(p.getMetadata())
                .build())
            .collect(Collectors.toList());
        
        List<OrderDetailDTO.OrderNoteDTO> noteDTOs = order.getNotes().stream()
            .map(n -> OrderDetailDTO.OrderNoteDTO.builder()
                .id(n.getId())
                .content(n.getContent())
                .type(n.getType())
                .createdBy(getUserName(n.getCreatedBy()))
                .createdAt(n.getCreatedAt())
                .isCustomerVisible(n.getIsCustomerVisible())
                .attachments(new ArrayList<>())
                .build())
            .collect(Collectors.toList());
        
        List<TimelineEventDTO> timelineDTOs = order.getTimeline().stream()
            .map(t -> TimelineEventDTO.builder()
                .id(t.getId())
                .type(t.getType())
                .status(t.getStatus())
                .description(t.getDescription())
                .timestamp(t.getTimestamp())
                .userId(t.getUserId())
                .userName(t.getUserName())
                .metadata(t.getMetadata())
                .build())
            .collect(Collectors.toList());
        
        OrderDetailDTO.DeliveryInfoDTO deliveryDTO = null;
        if (order.getDeliveryInfo() != null) {
            deliveryDTO = OrderDetailDTO.DeliveryInfoDTO.builder()
                .type(order.getDeliveryInfo().getType())
                .address(null)
                .scheduledTime(order.getDeliveryInfo().getScheduledTime())
                .deliveredAt(order.getDeliveryInfo().getDeliveredAt())
                .deliveredBy(getUserName(order.getDeliveryInfo().getDeliveredBy()))
                .proofOfDelivery(order.getDeliveryInfo().getProofOfDelivery())
                .recipientName(order.getDeliveryInfo().getRecipientName())
                .notes(order.getDeliveryInfo().getNotes())
                .build();
        }
        
        OrderDetailDTO.MetadataDTO metadata = OrderDetailDTO.MetadataDTO.builder()
            .createdBy(order.getCreatedBy())
            .createdAt(order.getCreatedAt())
            .updatedBy(order.getUpdatedBy())
            .updatedAt(order.getUpdatedAt())
            .ipAddress(null)
            .userAgent(null)
            .build();
        
        return OrderDetailDTO.builder()
            .id(summary.getId())
            .orderNumber(summary.getOrderNumber())
            .trackingNumber(summary.getTrackingNumber())
            .customer(summary.getCustomer())
            .shop(summary.getShop())
            .status(summary.getStatus())
            .itemCount(summary.getItemCount())
            .totalAmount(summary.getTotalAmount())
            .paidAmount(summary.getPaidAmount())
            .balanceDue(summary.getBalanceDue())
            .priority(summary.getPriority())
            .promisedDate(summary.getPromisedDate())
            .receivedAt(summary.getReceivedAt())
            .expectedReadyAt(summary.getExpectedReadyAt())
            .actualReadyAt(summary.getActualReadyAt())
            .completedAt(summary.getCompletedAt())
            .createdBy(summary.getCreatedBy())
            .createdAt(summary.getCreatedAt())
            .tags(summary.getTags())
            .items(itemDTOs)
            .discounts(new ArrayList<>())
            .payments(paymentDTOs)
            .delivery(deliveryDTO)
            .notes(noteDTOs)
            .timeline(timelineDTOs)
            .metadata(metadata)
            .build();
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ServiceSummary {
        private final String serviceType;
        private int count;
        private BigDecimal revenue;
    }
}