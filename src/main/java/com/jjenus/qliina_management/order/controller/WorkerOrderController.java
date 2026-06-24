// ./src/main/java/com/jjenus/qliina_management/order/controller/WorkerOrderController.java
package com.jjenus.qliina_management.order.controller;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.order.dto.*;
import com.jjenus.qliina_management.order.service.WorkerOrderService;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Worker Order Operations", description = "Scoped order operations for worker roles (Washer, Ironer, Delivery)")
@RestController
@RequestMapping("/api/v1/{businessId}/worker/orders")
@RequiredArgsConstructor
@Validated
public class WorkerOrderController {

    private final WorkerOrderService workerOrderService;

    @Operation(
        summary = "Lookup item by ID",
        description = "Workers can look up an item by its short ID or full UUID. " +
                     "This is the ONLY way workers access items they haven't previously interacted with."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item found"),
        @ApiResponse(responseCode = "404", description = "Item not found or not accessible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/items/lookup/{itemId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<WorkerItemDTO> lookupItem(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Item ID (short ID or UUID)", required = true)
            @Pattern(
                regexp = "^[A-Za-z0-9-]+$",
                message = "Invalid item ID format"
            )
            @PathVariable String itemId) {
        
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerOrderService.lookupItem(businessId, workerId, itemId));
    }

    @Operation(
        summary = "Get my work queue",
        description = "Returns items that are: 1) in this worker's queue (awaiting their role), " +
                     "or 2) currently being processed by this worker"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Queue retrieved"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/items/queue")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<WorkerItemDTO>> getWorkQueue(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Filter: ALL, PENDING, IN_PROGRESS")
            @RequestParam(defaultValue = "ALL") String filter,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 50, sort = "order.createdAt", direction = Sort.Direction.ASC) 
            Pageable pageable) {
        
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerOrderService.getWorkQueue(businessId, workerId, filter, pageable));
    }

    @Operation(
        summary = "Get items I've worked on",
        description = "Returns items this worker has previously interacted with"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "History retrieved"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/items/history")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<WorkerItemDTO>> getWorkHistory(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "lastInteraction", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerOrderService.getWorkHistory(businessId, workerId, pageable));
    }

    @Operation(
        summary = "Start working on an item",
        description = "Marks an item as being worked on by the current worker. " +
                     "Validates the status transition is allowed for this worker's role."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Work started"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items/{itemId}/start")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.status.update')")
    public ResponseEntity<WorkerItemDTO> startWorkOnItem(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Item ID (UUID)", required = true)
            @PathVariable UUID itemId) {
        
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(workerOrderService.startWorkOnItem(businessId, workerId, itemId));
    }

    @Operation(
        summary = "Complete work on an item",
        description = "Marks an item as completed by this worker. " +
                     "Validates the status transition. Items needing QC will be routed accordingly."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Work completed"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items/{itemId}/complete")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.status.update')")
    public ResponseEntity<WorkerItemDTO> completeWorkOnItem(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Item ID (UUID)", required = true)
            @PathVariable UUID itemId,
            
            @Valid @RequestBody(required = false) CompleteWorkRequest request) {
        
        UUID workerId = SecurityContextUtil.requireUserId();
        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(workerOrderService.completeWorkOnItem(businessId, workerId, itemId, notes));
    }
}