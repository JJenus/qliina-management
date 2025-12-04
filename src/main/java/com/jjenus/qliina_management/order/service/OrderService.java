package com.jjenus.qliina_management.order.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.customer.service.CustomerService;
import com.jjenus.qliina_management.identity.model.Shop;
import com.jjenus.qliina_management.identity.repository.ShopRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.dto.*;
import com.jjenus.qliina_management.order.model.*;
import com.jjenus.qliina_management.order.repository.OrderItemRepository;
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
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final QualityCheckRepository qualityCheckRepository;  // ADDED
    
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
        Order order = new Order();
        order.setBusinessId(businessId);
        order.setShopId(request.getShopId());
        order.setCustomerId(request.getCustomerId());
        order.setOrderNumber(generateOrderNumber(businessId));
        order.setTrackingNumber(generateTrackingNumber());
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
            item.setOrder(order);
            item.setItemNumber(generateItemNumber(order.getOrderNumber(), ++itemCount));
            item.setServiceType(itemDto.getServiceTypeId().toString());
            item.setGarmentType(itemDto.getGarmentTypeId() != null ? itemDto.getGarmentTypeId().toString() : null);
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
            
            items.add(item);
            totalAmount = totalAmount.add(subtotal);
        }
        
        order.setItems(items);
        order.setItemCount(items.size());
        
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
        
        order.setStatus(Order.OrderStatus.ARCHIVED);
        
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
    
    @Transactional
    public OrderStatusDTO updateOrderStatus(UUID orderId, UpdateStatusRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        Order.OrderStatus previousStatus = order.getStatus();
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(request.getStatus());
        
        // Validate status transition
        validateStatusTransition(previousStatus, newStatus);
        
        order.setStatus(newStatus);
        
        if (newStatus == Order.OrderStatus.READY_FOR_PICKUP) {
            order.setActualReadyAt(LocalDateTime.now());
        } else if (newStatus == Order.OrderStatus.COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
        }
        
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrder(order);
        timeline.setType("STATUS_CHANGE");
        timeline.setStatus(newStatus.toString());
        timeline.setDescription(request.getNotes());
        timeline.setTimestamp(LocalDateTime.now());
        timeline.setUserId(getCurrentUserId());
        timeline.setUserName(getUserName(getCurrentUserId()));
        order.getTimeline().add(timeline);
        
        order = orderRepository.save(order);
        
        return OrderStatusDTO.builder()
            .orderId(order.getId())
            .previousStatus(previousStatus.toString())
            .currentStatus(newStatus.toString())
            .updatedAt(LocalDateTime.now())
            .updatedBy(getUserName(getCurrentUserId()))
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
        
        item.setStatus(newStatus);
        
        ItemStatusHistory history = new ItemStatusHistory();
        history.setOrderItem(item);
        history.setStatus(newStatus.toString());
        history.setTimestamp(LocalDateTime.now());
        history.setUpdatedBy(getCurrentUserId());
        history.setNotes(request.getNotes());
        item.getStatusHistory().add(history);
        
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
    
    private void validateStatusTransition(Order.OrderStatus from, Order.OrderStatus to) {
        // Define allowed transitions
        Map<Order.OrderStatus, List<Order.OrderStatus>> allowedTransitions = Map.of(
            Order.OrderStatus.RECEIVED, List.of(Order.OrderStatus.WASHING, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.WASHING, List.of(Order.OrderStatus.WASHED, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.WASHED, List.of(Order.OrderStatus.IRONING, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.IRONING, List.of(Order.OrderStatus.IRONED, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.IRONED, List.of(Order.OrderStatus.QUALITY_CHECK, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.QUALITY_CHECK, List.of(Order.OrderStatus.READY_FOR_PICKUP, Order.OrderStatus.WASHING, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.READY_FOR_PICKUP, List.of(Order.OrderStatus.OUT_FOR_DELIVERY, Order.OrderStatus.COMPLETED, Order.OrderStatus.ARCHIVED),
            Order.OrderStatus.OUT_FOR_DELIVERY, List.of(Order.OrderStatus.COMPLETED, Order.OrderStatus.ARCHIVED)
        );
        
        List<Order.OrderStatus> allowed = allowedTransitions.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(
                String.format("Invalid status transition from %s to %s", from, to),
                "INVALID_STATUS_TRANSITION"
            );
        }
    }
    
    private String generateOrderNumber(UUID businessId) {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequence = String.format("%04d", new Random().nextInt(10000));
        return String.format("ORD-%s-%s-%s", businessId.toString().substring(0, 4), datePart, sequence);
    }
    
    private String generateTrackingNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.format("%06d", new Random().nextInt(1000000));
        return "TRK" + timestamp.substring(timestamp.length() - 8) + random;
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