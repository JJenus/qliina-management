package com.jjenus.qliina_management.inventory.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.inventory.dto.*;
import com.jjenus.qliina_management.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Inventory Management", description = "Inventory management endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/inventory")
@RequiredArgsConstructor
public class InventoryController {
    
    private final InventoryService inventoryService;
    
    // ==================== Inventory Items ====================
    
    @Operation(summary = "List inventory items", description = "Get paginated list of inventory items")
    @GetMapping("/items")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<InventoryItemDTO>> listItems(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @ModelAttribute InventoryFilter filter) {
        return ResponseEntity.ok(inventoryService.listItems(businessId, filter, pageable));
    }
    
    @Operation(summary = "Get inventory item", description = "Get inventory item details by ID")
    @GetMapping("/items/{itemId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<InventoryItemDetailDTO> getItem(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(inventoryService.getItem(itemId));
    }
    
    @Operation(summary = "Create inventory item", description = "Create a new inventory item")
    @PostMapping("/items")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<InventoryItemDTO> createItem(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.ok(inventoryService.createItem(businessId, request));
    }
    
    @Operation(summary = "Update inventory item", description = "Update an existing inventory item")
    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<InventoryItemDTO> updateItem(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ResponseEntity.ok(inventoryService.updateItem(itemId, request));
    }
    
    // ==================== Shop Stock ====================
    
    @Operation(summary = "Get shop stock", description = "Get stock levels for a specific shop")
    @GetMapping("/stock/{shopId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<List<ShopStockDTO>> getShopStock(
            @PathVariable UUID businessId,
            @PathVariable UUID shopId) {
        return ResponseEntity.ok(inventoryService.getShopStock(businessId, shopId));
    }
    
    @Operation(summary = "Adjust stock", description = "Adjust stock levels")
    @PostMapping("/stock/adjust")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<StockAdjustmentResultDTO> adjustStock(
            @PathVariable UUID businessId,
            @Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(businessId, request));
    }
    
    @Operation(summary = "Transfer stock", description = "Transfer stock between shops")
    @PostMapping("/stock/transfer")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<SuccessResponse> transferStock(
            @PathVariable UUID businessId,
            @Valid @RequestBody TransferStockRequest request) {
        inventoryService.transferStock(businessId, request);
        return ResponseEntity.ok(SuccessResponse.of("Stock transferred successfully"));
    }
    
    // ==================== Stock Transactions ====================
    
    @Operation(summary = "List stock transactions", description = "Get paginated list of stock transactions")
    @GetMapping("/transactions")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<StockTransactionDTO>> listTransactions(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID shopId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listTransactions(
            businessId, shopId, itemId, type, fromDate, toDate, pageable));
    }
    
    // ==================== Stock Alerts ====================
    
    @Operation(summary = "Get stock alerts", description = "Get active stock alerts")
    @GetMapping("/alerts")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<List<StockAlertDTO>> getStockAlerts(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID shopId) {
        return ResponseEntity.ok(inventoryService.getStockAlerts(businessId, shopId));
    }
    
    @Operation(summary = "Acknowledge alert", description = "Acknowledge a stock alert")
    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<SuccessResponse> acknowledgeAlert(
            @PathVariable UUID businessId,
            @PathVariable UUID alertId) {
        inventoryService.acknowledgeAlert(alertId);
        return ResponseEntity.ok(SuccessResponse.of("Alert acknowledged"));
    }
    
    @Operation(summary = "Resolve alert", description = "Resolve a stock alert")
    @PostMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<SuccessResponse> resolveAlert(
            @PathVariable UUID businessId,
            @PathVariable UUID alertId) {
        inventoryService.resolveAlert(alertId);
        return ResponseEntity.ok(SuccessResponse.of("Alert resolved"));
    }
    
    // ==================== Suppliers ====================
    
    @Operation(summary = "List suppliers", description = "Get paginated list of suppliers")
    @GetMapping("/suppliers")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<SupplierDTO>> listSuppliers(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listSuppliers(businessId, search, pageable));
    }
    
    @Operation(summary = "Get supplier", description = "Get supplier details by ID")
    @GetMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<SupplierDTO> getSupplier(
            @PathVariable UUID businessId,
            @PathVariable UUID supplierId) {
        return ResponseEntity.ok(inventoryService.getSupplier(supplierId));
    }
    
    @Operation(summary = "Create supplier", description = "Create a new supplier")
    @PostMapping("/suppliers")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<SupplierDTO> createSupplier(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(inventoryService.createSupplier(businessId, request));
    }
    
    @Operation(summary = "Update supplier", description = "Update an existing supplier")
    @PutMapping("/suppliers/{supplierId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<SupplierDTO> updateSupplier(
            @PathVariable UUID businessId,
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(inventoryService.updateSupplier(supplierId, request));
    }
    
    // ==================== Purchase Orders ====================
    
    @Operation(summary = "List purchase orders", description = "Get paginated list of purchase orders")
    @GetMapping("/purchase-orders")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PageResponse<PurchaseOrderDTO>> listPurchaseOrders(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID shopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listPurchaseOrders(
            businessId, supplierId, shopId, status, fromDate, toDate, pageable));
    }
    
    @Operation(summary = "Get purchase order", description = "Get purchase order details by ID")
    @GetMapping("/purchase-orders/{poId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.view')")
    public ResponseEntity<PurchaseOrderDTO> getPurchaseOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID poId) {
        return ResponseEntity.ok(inventoryService.getPurchaseOrder(poId));
    }
    
    @Operation(summary = "Create purchase order", description = "Create a new purchase order")
    @PostMapping("/purchase-orders")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<PurchaseOrderDTO> createPurchaseOrder(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.ok(inventoryService.createPurchaseOrder(businessId, request));
    }
    
    @Operation(summary = "Update purchase order status", description = "Update purchase order status")
    @PatchMapping("/purchase-orders/{poId}/status")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<PurchaseOrderDTO> updatePurchaseOrderStatus(
            @PathVariable UUID businessId,
            @PathVariable UUID poId,
            @Valid @RequestBody UpdatePurchaseOrderStatusRequest request) {
        return ResponseEntity.ok(inventoryService.updatePurchaseOrderStatus(poId, request));
    }
    
    @Operation(summary = "Receive purchase order", description = "Receive items from purchase order")
    @PostMapping("/purchase-orders/{poId}/receive")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.adjust')")
    public ResponseEntity<PurchaseOrderDTO> receivePurchaseOrder(
            @PathVariable UUID businessId,
            @PathVariable UUID poId,
            @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return ResponseEntity.ok(inventoryService.receivePurchaseOrder(poId, request));
    }
}
