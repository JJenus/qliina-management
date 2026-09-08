package com.jjenus.qliina_management.payment.controller;

import com.jjenus.qliina_management.common.RequireClockIn;
import com.jjenus.qliina_management.payment.dto.PaymentProviderDTO;
import com.jjenus.qliina_management.payment.dto.PaymentVerifyDTO;
import com.jjenus.qliina_management.payment.service.PaymentProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payment Providers", description = "Payment provider catalog and per-business enablement")
@RestController
@RequestMapping("/api/v1/{businessId}/payment-providers")
@RequiredArgsConstructor
@RequireClockIn
public class PaymentProviderController {

    private final PaymentProviderService paymentProviderService;

    @Operation(
        summary = "List payment providers",
        description = "Available providers with per-business enabled/configured/available state"
    )
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<List<PaymentProviderDTO>> listProviders(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(paymentProviderService.listProviders(businessId));
    }

    @Operation(
        summary = "Enable/disable a payment provider",
        description = "Opt a business in or out of a specific online payment provider"
    )
    @PatchMapping("/{provider}/enabled")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<PaymentProviderDTO> setProviderEnabled(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Parameter(description = "Provider name", required = true)
            @PathVariable String provider,
            @Parameter(description = "Desired enabled state", required = true)
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(paymentProviderService.setProviderEnabled(businessId, provider, enabled));
    }

    @Operation(
        summary = "Verify a provider-backed payment",
        description = "Poll the payment provider for the final status of a pending charge"
    )
    @PostMapping("/{paymentId}/verify")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PaymentVerifyDTO> verifyPayment(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Parameter(description = "Payment ID", required = true)
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentProviderService.verifyPayment(businessId, paymentId));
    }
}