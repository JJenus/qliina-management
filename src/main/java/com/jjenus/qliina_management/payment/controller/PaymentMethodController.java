// ./src/main/java/com/jjenus/qliina_management/payment/controller/PaymentMethodController.java
package com.jjenus.qliina_management.payment.controller;

import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.payment.dto.PaymentMethodDTO;
import com.jjenus.qliina_management.payment.dto.CreatePaymentMethodRequest;
import com.jjenus.qliina_management.payment.dto.UpdatePaymentMethodRequest;
import com.jjenus.qliina_management.payment.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payment Methods", description = "Manage available payment methods for a business")
@RestController
@RequestMapping("/api/v1/{businessId}/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @Operation(summary = "List payment methods", 
               description = "Get all active payment methods for the business")
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<List<PaymentMethodDTO>> listPaymentMethods(
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(paymentMethodService.getActivePaymentMethods(businessId));
    }

    @Operation(summary = "Get payment method details")
    @GetMapping("/{methodId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PaymentMethodDTO> getPaymentMethod(
            @PathVariable UUID businessId,
            @PathVariable UUID methodId) {
        return ResponseEntity.ok(paymentMethodService.getPaymentMethod(methodId));
    }

    @Operation(summary = "Create custom payment method",
               description = "Add a new payment method for the business")
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<PaymentMethodDTO> createPaymentMethod(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreatePaymentMethodRequest request) {
        return ResponseEntity.ok(paymentMethodService.createPaymentMethod(businessId, request));
    }

    @Operation(summary = "Update payment method")
    @PutMapping("/{methodId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<PaymentMethodDTO> updatePaymentMethod(
            @PathVariable UUID businessId,
            @PathVariable UUID methodId,
            @Valid @RequestBody UpdatePaymentMethodRequest request) {
        return ResponseEntity.ok(paymentMethodService.updatePaymentMethod(methodId, request));
    }

    @Operation(summary = "Enable/disable payment method")
    @PatchMapping("/{methodId}/toggle")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<PaymentMethodDTO> togglePaymentMethod(
            @PathVariable UUID businessId,
            @PathVariable UUID methodId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(paymentMethodService.togglePaymentMethod(methodId, active));
    }

    @Operation(summary = "Delete custom payment method")
    @DeleteMapping("/{methodId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> deletePaymentMethod(
            @PathVariable UUID businessId,
            @PathVariable UUID methodId) {
        paymentMethodService.deletePaymentMethod(methodId);
        return ResponseEntity.ok(SuccessResponse.of("Payment method deleted"));
    }
}