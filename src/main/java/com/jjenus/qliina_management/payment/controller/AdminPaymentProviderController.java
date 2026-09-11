package com.jjenus.qliina_management.payment.controller;

import com.jjenus.qliina_management.payment.dto.AdminPaymentProviderDTO;
import com.jjenus.qliina_management.payment.service.PlatformPaymentProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Platform-wide payment-provider connectivity control. A provider disabled here is
 * hidden from every business's gateway screen and fails closed at checkout until
 * re-enabled — the platform admin is the single gatekeeper for which processors
 * tenants may take payments through.
 */
@Tag(name = "Platform Payment Providers", description =
        "Platform-wide control over which payment providers businesses can connect")
@RestController
@RequestMapping("/api/v1/admin/payment-providers")
@RequiredArgsConstructor
public class AdminPaymentProviderController {

    private final PlatformPaymentProviderService platformPaymentProviderService;

    @Operation(summary = "List providers",
            description = "Gateway catalog with platform availability and per-provider business connection counts")
    @GetMapping
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.payments.manage')
        or hasPermission(null, 'PLATFORM', 'platform.businesses.view')
    """)
    public ResponseEntity<List<AdminPaymentProviderDTO>> listProviders() {
        return ResponseEntity.ok(platformPaymentProviderService.list());
    }

    @Operation(summary = "Set provider availability",
            description = "Enable or disable a provider platform-wide. Disabled providers are "
                    + "hidden from business gateway screens and fail closed at checkout until re-enabled. "
                    + "Already-initiated authorizations keep verifying/refunding.")
    @PatchMapping("/{provider}/availability")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.payments.manage')")
    public ResponseEntity<AdminPaymentProviderDTO> setProviderAvailability(
            @PathVariable String provider,
            @RequestParam boolean platformEnabled) {
        return ResponseEntity.ok(platformPaymentProviderService.setPlatformEnabled(provider, platformEnabled));
    }
}