// ./src/main/java/com/jjenus/qliina_management/inventory/controller/WorkerInventoryController.java
package com.jjenus.qliina_management.inventory.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.inventory.dto.LogStockUsageRequest;
import com.jjenus.qliina_management.inventory.dto.StockUsageDTO;
import com.jjenus.qliina_management.inventory.dto.WorkerStockItemDTO;
import com.jjenus.qliina_management.inventory.service.WorkerInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Worker-facing inventory endpoints: consumable pickers, stock usage
 * logging and personal usage history.
 */
@Tag(name = "Worker Inventory", description = "Stock usage logging and supply visibility for worker roles")
@RestController
@RequestMapping("/api/v1/{businessId}/worker/inventory")
@RequiredArgsConstructor
public class WorkerInventoryController {

    private final WorkerInventoryService workerInventoryService;

    @Operation(
        summary = "List consumables in my shop",
        description = "Active inventory items with current stock levels in the worker's primary shop. " +
                     "Used by the materials-used picker and the supply request form."
    )
    @GetMapping("/items")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.use')")
    public ResponseEntity<List<WorkerStockItemDTO>> listConsumables(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerInventoryService.listConsumableItems(businessId, workerId));
    }

    @Operation(
        summary = "Log stock used",
        description = "Records consumables used while processing an order, deducts shop stock and " +
                     "raises a low-stock alert when the reorder point is crossed."
    )
    @PostMapping("/usage")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.use')")
    public ResponseEntity<StockUsageDTO> logUsage(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Valid @RequestBody LogStockUsageRequest request) {
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerInventoryService.logUsage(businessId, workerId, request));
    }

    @Operation(
        summary = "My usage history",
        description = "Stock usage entries logged by the authenticated worker, newest first."
    )
    @GetMapping("/usage/mine")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.use')")
    public ResponseEntity<PageResponse<StockUsageDTO>> myUsages(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerInventoryService.myUsages(businessId, workerId, pageable));
    }
}
