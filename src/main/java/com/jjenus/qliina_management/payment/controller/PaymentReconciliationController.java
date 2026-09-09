package com.jjenus.qliina_management.payment.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.payment.dto.PaymentReconciliationItemDTO;
import com.jjenus.qliina_management.payment.dto.ResolveReconciliationRequest;
import com.jjenus.qliina_management.payment.service.PaymentReconciliationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Payment Reconciliation", description = "Unmatched-funds queue: provider settlements with no matching payment")
@RestController
@RequestMapping("/api/v1/{businessId}/payment-reconciliation")
@RequiredArgsConstructor
public class PaymentReconciliationController {

    private final PaymentReconciliationService service;

    @Operation(
        summary = "List reconciliation queue",
        description = "OPEN items are visible across businesses (global queue); RESOLVED items are " +
                "scoped to this business. Pass ?status=OPEN or ?status=RESOLVED to narrow the base set."
    )
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PageResponse<PaymentReconciliationItemDTO>> list(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,

            @Parameter(description = "Item status filter (OPEN/RESOLVED)")
            @RequestParam(required = false) String status,

            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.list(businessId, status, pageable));
    }

    @Operation(
        summary = "Resolve a reconciliation item",
        description = "Links an OPEN unmatched-funds item to an order (and therefore this business), " +
                "marking it RESOLVED."
    )
    @PostMapping("/{itemId}/resolve")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.process')")
    public ResponseEntity<PaymentReconciliationItemDTO> resolve(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,

            @Parameter(description = "Reconciliation item ID", required = true)
            @PathVariable UUID itemId,

            @Valid @RequestBody ResolveReconciliationRequest request) {
        return ResponseEntity.ok(service.resolve(businessId, itemId, request));
    }
}