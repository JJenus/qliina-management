package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Changes how a business connects to an online payment provider.
 *
 * <p>{@code mode=PLATFORM|BYO} also enables the provider; {@code mode=DISCONNECTED}
 * disables it and clears stored credentials. {@code secretKey} is required for BYO
 * and {@code platformSubaccountId} only where the provider supports subaccounts.
 * Credentials are encrypted at rest and never returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProviderConnectionRequest {
    private String mode;
    private String secretKey;
    private String platformSubaccountId;
}