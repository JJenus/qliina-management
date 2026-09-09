package com.jjenus.qliina_management.payment.model;

/**
 * How a business connects to an online payment processor.
 *
 * <p>{@link #DISCONNECTED} is "no gateway" — manual POS recording of CARD/TRANSFER
 * remains the always-on core. {@link #PLATFORM} routes through Qliina's own
 * processor account (subaccounts, where supported). {@link #BYO} uses the
 * business's own credentials (bring-your-own), stored encrypted and never echoed.
 */
public enum ProviderConnectionMode {
    DISCONNECTED,
    PLATFORM,
    BYO
}