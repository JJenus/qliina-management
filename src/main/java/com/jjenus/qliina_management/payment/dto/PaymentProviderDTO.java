package com.jjenus.qliina_management.payment.dto;

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
    /** Provider is ready to take live charges (secrets present / allowed in env). */
    private boolean configured;
    /** Checkout-eligible: enabled AND configured. */
    private boolean available;
}