package com.jjenus.qliina_management.payment.dto;

import com.jjenus.qliina_management.payment.model.ProviderConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Checkout/admin view of one payment provider for a business. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProviderDTO {
    private String name;
    private String displayName;
    private List<String> methods;
    /** Business admin toggle (persisted per business; defaults apply when unset). */
    private boolean enabled;
    /** Provider's platform side is ready to take live charges (platform secrets present). */
    private boolean configured;
    /**
     * Checkout-eligible: enabled AND connectable. A provider is connectable in
     * {@code PLATFORM} mode only when the platform is configured, or in {@code BYO}
     * mode only when business credentials are stored.
     */
    private boolean available;
    /** How this business connects: DISCONNECTED, PLATFORM or BYO. */
    private ProviderConnectionMode connectionMode;
    /** True when this business has stored its own (never echoed) credentials. */
    private boolean hasCredentials;
    /** Platform subaccount routed to on charge, when in PLATFORM mode. */
    private String platformSubaccountId;
    /** Whether this provider can route through a platform subaccount at all. */
    private boolean supportsPlatformSubaccounts;
}