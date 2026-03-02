package com.jjenus.qliina_management.inventory.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.inventory.dto.*;
import com.jjenus.qliina_management.inventory.service.InventoryService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Inventory Management", description = "Complete inventory management endpoints for items, stock, suppliers, and purchase orders")
@RestController
@RequestMapping("/api/v1/{businessId}/inventory")
@RequiredArgsConstructor
public class InventoryController {
    
    private final InventoryService inventoryService;
    
    // ==================== Inventory Items ====================
    
    @Operation(
        summary = "List inventory items",
        description = "Get paginated list of inventory items with optional filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved items"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/items")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<InventoryItemDTO>> listItems(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            
            @Parameter(description = "Filter criteria for items")
            @ModelAttribute InventoryFilter filter) {
        return ResponseEntity.ok(inventoryService.listItems(businessId, filter, pageable));
    }
    
    @Operation(
        summary = "Get inventory item",
        description = "Get detailed inventory item information by ID including shop stocks and transactions"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved item"),
        @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/items/{itemId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<InventoryItemDetailDTO> getItem(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Item ID", required = true)
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(inventoryService.getItem(itemId));
    }
    
    @Operation(
        summary = "Create inventory item",
        description = "Create a new inventory item with SKU validation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data or duplicate SKU",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<InventoryItemDTO> createItem(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.ok(inventoryService.createItem(businessId, request));
    }
    
    @Operation(
        summary = "Update inventory item",
        description = "Update an existing inventory item"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item updated successfully"),
        @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid update data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<InventoryItemDTO> updateItem(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Item ID", required = true)
            @PathVariable UUID itemId,
            
            @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(inventoryService.updateItem(itemId, request));
    }
    
    // ==================== Shop Stock ====================
    
    @Operation(
        summary = "Get shop stock",
        description = "Get current stock levels for all items in a specific shop"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved stock"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/stock/{shopId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<List<ShopStockDTO>> getShopStock(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Shop ID", required = true)
            @PathVariable UUID shopId) {
        return ResponseEntity.ok(inventoryService.getShopStock(businessId, shopId));
    }
    
    @Operation(
        summary = "Adjust stock",
        description = "Manually adjust stock levels for multiple items (add or remove)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock adjustments processed"),
        @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Insufficient stock for removal",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/stock/adjust")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<StockAdjustmentResultDTO> adjustStock(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(businessId, request));
    }
    
    @Operation(
        summary = "Transfer stock",
        description = "Transfer stock quantity from one shop to another"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock transferred successfully"),
        @ApiResponse(responseCode = "404", description = "Item or shop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Insufficient stock in source shop",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/stock/transfer")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<SuccessResponse> transferStock(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody TransferStockRequest request) {
        inventoryService.transferStock(businessId, request);
        return ResponseEntity.ok(SuccessResponse.of("Stock transferred successfully"));
    }
    
    // ==================== Stock Transactions ====================
    
    @Operation(
        summary = "List stock transactions",
        description = "Get paginated list of stock transactions with filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved transactions"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/transactions")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<StockTransactionDTO>> listTransactions(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Filter by shop ID")
            @RequestParam(required = false) UUID shopId,
            
            @Parameter(description = "Filter by item ID")
            @RequestParam(required = false) UUID itemId,
            
            @Parameter(description = "Filter by transaction type")
            @RequestParam(required = false) String type,
            
            @Parameter(description = "Filter by start date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            
            @Parameter(description = "Filter by end date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listTransactions(
            businessId, shopId, itemId, type, fromDate, toDate, pageable));
    }
    
    // ==================== Stock Alerts ====================
    
    @Operation(
        summary = "Get stock alerts",
        description = "Get active stock alerts for low or critical stock levels"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved alerts"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/alerts")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<List<StockAlertDTO>> getStockAlerts(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Filter by shop ID")
            @RequestParam(required = false) UUID shopId) {
        return ResponseEntity.ok(inventoryService.getStockAlerts(businessId, shopId));
    }
    
    @Operation(
        summary = "Acknowledge alert",
        description = "Mark a stock alert as acknowledged"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alert acknowledged"),
        @ApiResponse(responseCode = "404", description = "Alert not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<SuccessResponse> acknowledgeAlert(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Alert ID", required = true)
            @PathVariable UUID alertId) {
        inventoryService.acknowledgeAlert(alertId);
        return ResponseEntity.ok(SuccessResponse.of("Alert acknowledged"));
    }
    
    @Operation(
        summary = "Resolve alert",
        description = "Mark a stock alert as resolved"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alert resolved"),
        @ApiResponse(responseCode = "404", description = "Alert not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<SuccessResponse> resolveAlert(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Alert ID", required = true)
            @PathVariable UUID alertId) {
        inventoryService.resolveAlert(alertId);
        return ResponseEntity.ok(SuccessResponse.of("Alert resolved"));
    }
    
    // ==================== Suppliers ====================
    
    @Operation(
        summary = "List suppliers",
        description = "Get paginated list of suppliers with optional search"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved suppliers"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/suppliers")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<SupplierDTO>> listSuppliers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Search query (searches in name, contact, email)")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listSuppliers(businessId, search, pageable));
    }
    
    @Operation(
        summary = "Get supplier",
        description = "Get supplier details by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved supplier"),
        @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<SupplierDTO> getSupplier(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Supplier ID", required = true)
            @PathVariable UUID supplierId) {
        return ResponseEntity.ok(inventoryService.getSupplier(supplierId));
    }
    
    @Operation(
        summary = "Create supplier",
        description = "Create a new supplier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Supplier created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid supplier data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/suppliers")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<SupplierDTO> createSupplier(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(inventoryService.createSupplier(businessId, request));
    }
    
    @Operation(
        summary = "Update supplier",
        description = "Update an existing supplier"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Supplier updated successfully"),
        @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid update data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<SupplierDTO> updateSupplier(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Supplier ID", required = true)
            @PathVariable UUID supplierId,
            
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(inventoryService.updateSupplier(supplierId, request));
    }
    
    // ==================== Purchase Orders ====================
    
    @Operation(
        summary = "List purchase orders",
        description = "Get paginated list of purchase orders with filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved purchase orders"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/purchase-orders")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<PurchaseOrderDTO>> listPurchaseOrders(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Filter by supplier ID")
            @RequestParam(required = false) UUID supplierId,
            
            @Parameter(description = "Filter by shop ID")
            @RequestParam(required = false) UUID shopId,
            
            @Parameter(description = "Filter by order status")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Filter by start date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            
            @Parameter(description = "Filter by end date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listPurchaseOrders(
            businessId, supplierId, shopId, status, fromDate, toDate, pageable));
    }
    
    @Operation(
        summary = "Get purchase order",
        description = "Get purchase order details by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved purchase order"),
        @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/purchase-orders/{poId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PurchaseOrderDTO> getPurchaseOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Purchase Order ID", required = true)
            @PathVariable UUID poId) {
        return ResponseEntity.ok(inventoryService.getPurchaseOrder(poId));
    }
    
    @Operation(
        summary = "Create purchase order",
        description = "Create a new purchase order"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Purchase order created successfully"),
        @ApiResponse(responseCode = "404", description = "Supplier or item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid order data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/purchase-orders")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<PurchaseOrderDTO> createPurchaseOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.ok(inventoryService.createPurchaseOrder(businessId, request));
    }
    
    @Operation(
        summary = "Update purchase order status",
        description = "Update the status of a purchase order"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/purchase-orders/{poId}/status")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<PurchaseOrderDTO> updatePurchaseOrderStatus(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Purchase Order ID", required = true)
            @PathVariable UUID poId,
            
            @Valid @RequestBody UpdatePurchaseOrderStatusRequest request) {
        return ResponseEntity.ok(inventoryService.updatePurchaseOrderStatus(poId, request));
    }
    
    @Operation(
        summary = "Receive purchase order",
        description = "Receive items from a purchase order and update stock"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Purchase order received successfully"),
        @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid receipt data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/purchase-orders/{poId}/receive")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<PurchaseOrderDTO> receivePurchaseOrder(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Purchase Order ID", required = true)
            @PathVariable UUID poId,
            
            @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return ResponseEntity.ok(inventoryService.receivePurchaseOrder(poId, request));
    }
}