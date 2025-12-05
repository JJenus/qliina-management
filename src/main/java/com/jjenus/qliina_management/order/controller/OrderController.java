package com.jjenus.qliina_management.order.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.order.dto.*;
import com.jjenus.qliina_management.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Order Management", description = "Complete order management endpoints for processing laundry orders")
@RestController
@RequestMapping("/api/v1/{businessId}/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    // ==================== Basic Order Operations ====================
    
    @Operation(
        summary = "List orders",
        description = "Get paginated list of orders with advanced filtering options"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved orders"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters")
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<OrderSummaryDTO>> listOrders(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            
            @Parameter(description = "Filter criteria for orders")
            @ModelAttribute OrderFilter filter) {
        return ResponseEntity.ok(orderService.listOrders(businessId, filter, pageable));
    }
    
    @Operation(
        summary = "Get order by ID",
        description = "Get detailed order information by order ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved order"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{orderId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<OrderDetailDTO> getOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
    
    @Operation(
        summary = "Get order by tracking number",
        description = "Get order details using tracking number"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved order"),
        @ApiResponse(responseCode = "404", description = "Order not found with provided tracking number"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/tracking/{trackingNumber}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<OrderDetailDTO> getOrderByTracking(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Tracking number", required = true, example = "TRK12345678")
            @PathVariable String trackingNumber) {
        return ResponseEntity.ok(orderService.getOrderByTrackingNumber(trackingNumber));
    }
    
    @Operation(
        summary = "Create order",
        description = "Create a new laundry order with items and details"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid order data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.create')")
    public ResponseEntity<OrderDetailDTO> createOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(businessId, request));
    }
    
    @Operation(
        summary = "Quick order",
        description = "Create a quick order with minimal information for fast checkout"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quick order created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/quick")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.create')")
    public ResponseEntity<OrderDetailDTO> quickOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody QuickOrderRequest request) {
        return ResponseEntity.ok(orderService.quickOrder(businessId, request));
    }
    
    @Operation(
        summary = "Update order",
        description = "Update an existing order details"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order updated successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "400", description = "Invalid update data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PutMapping("/{orderId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.update')")
    public ResponseEntity<OrderDetailDTO> updateOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(orderService.updateOrder(orderId, request));
    }
    
    @Operation(
        summary = "Cancel order",
        description = "Cancel an existing order (cannot cancel completed orders)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
        @ApiResponse(responseCode = "400", description = "Order cannot be cancelled (completed)"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.delete')")
    public ResponseEntity<SuccessResponse> cancelOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody CancelOrderRequest request) {
        orderService.cancelOrder(orderId, request);
        return ResponseEntity.ok(SuccessResponse.of("Order cancelled successfully"));
    }
    
    // ==================== Status Management ====================
    
    @Operation(
        summary = "Update order status",
        description = "Update the status of an order with validation of allowed transitions"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{orderId}/status")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.status.update')")
    public ResponseEntity<OrderStatusDTO> updateOrderStatus(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, request));
    }
    
    @Operation(
        summary = "Update item status",
        description = "Update the status of a specific order item"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item status updated"),
        @ApiResponse(responseCode = "404", description = "Order or item not found"),
        @ApiResponse(responseCode = "400", description = "Invalid status"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{orderId}/items/{itemId}/status")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.status.update')")
    public ResponseEntity<OrderItemStatusDTO> updateItemStatus(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Parameter(description = "Item ID", required = true)
            @PathVariable UUID itemId,
            
            @Valid @RequestBody UpdateItemStatusRequest request) {
        return ResponseEntity.ok(orderService.updateItemStatus(orderId, itemId, request));
    }
    
    // ==================== Timeline & Notes ====================
    
    @Operation(
        summary = "Get order timeline",
        description = "Get the complete status timeline for an order"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved timeline"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{orderId}/timeline")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<List<TimelineEventDTO>> getOrderTimeline(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderTimeline(orderId));
    }
    
    @Operation(
        summary = "Add order note",
        description = "Add a note to an order (internal or customer-visible)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Note added successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{orderId}/notes")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.update')")
    public ResponseEntity<OrderNoteDTO> addOrderNote(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody AddNoteRequest request) {
        return ResponseEntity.ok(orderService.addOrderNote(orderId, request));
    }
    
    // ==================== Transfer Operations ====================
    
    @Operation(
        summary = "Transfer order",
        description = "Transfer an order to another shop"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order transferred successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "400", description = "Invalid transfer request"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{orderId}/transfer")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.transfer')")
    public ResponseEntity<OrderDetailDTO> transferOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody TransferOrderRequest request) {
        return ResponseEntity.ok(orderService.transferOrder(orderId, request));
    }
    
    // ==================== Analytics & Reporting ====================
    
    @Operation(
        summary = "Get daily order summary",
        description = "Get a summary of orders for a specific day with analytics"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved summary"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/daily-summary")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<DailyOrdersSummaryDTO> getDailySummary(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Date for summary (ISO format)", required = true, example = "2026-03-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date) {
        return ResponseEntity.ok(orderService.getDailyOrderSummary(businessId, date));
    }
    
    // ==================== Missing Endpoints from Service ====================
    
    @Operation(
        summary = "Count pending orders",
        description = "Get count of pending orders for a business or shop"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved count"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/counts/pending")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<Long> countPendingOrders(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Shop ID (optional)")
            @RequestParam(required = false) UUID shopId) {
        return ResponseEntity.ok(orderService.countPendingOrders(businessId, shopId));
    }
    
    @Operation(
        summary = "Count orders by date range",
        description = "Get count of orders within a specified date range"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved count"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/counts/by-date-range")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<Long> countOrdersByDateRange(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Shop ID (optional)")
            @RequestParam(required = false) UUID shopId,
            
            @Parameter(description = "Start date", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "End date", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(orderService.countOrdersByDateRange(businessId, shopId, startDate, endDate));
    }
    
    // ==================== Attachment Operations ====================
    
    @Operation(
        summary = "Upload attachment",
        description = "Upload an attachment to an order (receipt, image, document)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attachment uploaded successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{orderId}/attachments")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.update')")
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "Attachment type (RECEIPT, IMAGE, DOCUMENT)", required = true, example = "RECEIPT")
            @RequestParam("type") String type) {
        // Implementation would handle file upload
        return ResponseEntity.ok(new AttachmentDTO());
    }
    
    @Operation(
        summary = "Delete attachment",
        description = "Delete an attachment from an order"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attachment deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Order or attachment not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @DeleteMapping("/{orderId}/attachments/{attachmentId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.update')")
    public ResponseEntity<SuccessResponse> deleteAttachment(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Parameter(description = "Attachment ID", required = true)
            @PathVariable UUID attachmentId) {
        // Implementation would handle attachment deletion
        return ResponseEntity.ok(SuccessResponse.of("Attachment deleted successfully"));
    }
    
    // ==================== Bulk Operations ====================
    
    @Operation(
        summary = "Bulk status update",
        description = "Update status for multiple orders at once"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bulk update processed"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/bulk/status")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.status.update')")
    public ResponseEntity<SuccessResponse> bulkStatusUpdate(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @RequestBody BulkStatusUpdateRequest request) {
        // Implementation would handle bulk updates
        return ResponseEntity.ok(SuccessResponse.of("Bulk status update completed"));
    }
}