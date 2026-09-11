package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Platform-admin view of one payment provider's platform-wide availability. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentProviderDTO {
    private String name;
    private String displayName;
    private List<String> methods;
    /** The platform gateway itself is configured (server secrets present). */
    private boolean configured;
    /**
     * Platform-level availability — when false, no business can connect this
     * provider or start a checkout through it.
     */
    private boolean platformEnabled;
    /** Whether this provider can route through a platform subaccount at all. */
    private boolean supportsPlatformSubaccounts;
    /** Businesses with an active (enabled) connection to this provider. */
    private long businessesConnected;
}