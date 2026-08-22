// ./src/main/java/com/jjenus/qliina_management/inventory/controller/StockRequestController.java
package com.jjenus.qliina_management.inventory.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.inventory.dto.CreateStockRequestRequest;
import com.jjenus.qliina_management.inventory.dto.ReviewStockRequestRequest;
import com.jjenus.qliina_management.inventory.dto.StockRequestDTO;
import com.jjenus.qliina_management.inventory.service.StockRequestService;
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

import java.util.UUID;

/**
 * Supply request flow.
 *
 * Worker endpoints (inventory.request): create / list mine / cancel.
 * Manager endpoints (inventory.manage): review queue + approve/reject/fulfill.
 */
@Tag(name = "Stock Requests", description = "Worker supply requests and managerial review")
@RestController
@RequestMapping("/api/v1/{businessId}")
@RequiredArgsConstructor
public class StockRequestController {

    private final StockRequestService stockRequestService;

    // ── worker ───────────────────────────────────────────────────────────────

    @Operation(summary = "Create a supply request",
        description = "Workers request restock of an inventory item for their primary shop.")
    @PostMapping("/worker/inventory/requests")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.request')")
    public ResponseEntity<StockRequestDTO> create(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateStockRequestRequest request) {
        UUID userId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(stockRequestService.create(businessId, userId, request));
    }

    @Operation(summary = "My supply requests")
    @GetMapping("/worker/inventory/requests")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.request')")
    public ResponseEntity<PageResponse<StockRequestDTO>> myRequests(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        UUID userId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(stockRequestService.myRequests(businessId, userId, pageable));
    }

    @Operation(summary = "Cancel my pending request")
    @DeleteMapping("/worker/inventory/requests/{requestId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.request')")
    public ResponseEntity<StockRequestDTO> cancel(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @PathVariable UUID requestId) {
        UUID userId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(stockRequestService.cancel(businessId, userId, requestId));
    }

    // ── manager ──────────────────────────────────────────────────────────────

    @Operation(summary = "Review queue",
        description = "All supply requests for the business, optionally filtered by status.")
    @GetMapping("/inventory/stock-requests")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<PageResponse<StockRequestDTO>> listForReview(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Parameter(description = "Filter: PENDING | APPROVED | REJECTED | FULFILLED | CANCELLED")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(stockRequestService.listForReview(businessId, status, pageable));
    }

    @Operation(summary = "Review a supply request",
        description = "action: APPROVE | REJECT (pending only) or FULFILL (adds stock).")
    @PatchMapping("/inventory/stock-requests/{requestId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'inventory.manage')")
    public ResponseEntity<StockRequestDTO> review(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewStockRequestRequest request) {
        UUID reviewerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(stockRequestService.review(businessId, reviewerId, requestId, request));
    }
}
