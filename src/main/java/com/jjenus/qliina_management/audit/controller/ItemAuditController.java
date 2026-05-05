// ./src/main/java/com/jjenus/qliina_management/audit/controller/ItemAuditController.java
package com.jjenus.qliina_management.audit.controller;

import com.jjenus.qliina_management.audit.dto.ItemAuditEntryDTO;
import com.jjenus.qliina_management.audit.service.ItemAuditService;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Item Audit", description = "Audit trail endpoints for order items")
@RestController
@RequestMapping("/api/v1/{businessId}/audit/items")
@RequiredArgsConstructor
public class ItemAuditController {

    private final ItemAuditService itemAuditService;

    @Operation(
        summary = "Get item audit trail",
        description = "Returns the complete audit trail for an order item. " +
                     "Accessible by managers/owners or workers who interacted with the item."
    )
    @GetMapping("/{itemId}/trail")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.audit') or " +
                  "hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<ItemAuditEntryDTO>> getItemAuditTrail(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        UUID requestingUserId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(
            itemAuditService.getItemAuditTrail(businessId, itemId, requestingUserId, pageable));
    }

    @Operation(
        summary = "Get my interactions with an item",
        description = "Returns only the current worker's interactions with a specific item."
    )
    @GetMapping("/{itemId}/my-interactions")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<ItemAuditEntryDTO>> getMyItemInteractions(
            @PathVariable UUID businessId,
            @PathVariable UUID itemId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        UUID workerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(
            itemAuditService.getWorkerItemHistory(workerId, itemId, pageable));
    }
}