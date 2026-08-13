package com.jjenus.qliina_management.notification.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Deterministic failure injection for provider dispatch, mirroring the billing
 * module's {@code SimulatedPaymentGateway}. Defaults are off; integration tests
 * flip the flags to drive the retry/exhaustion and permanent-failure paths.
 */
@Component
public class SimulatedChannelGateway {

    @Getter
    @Setter
    private volatile boolean forceFailures = false;

    @Getter
    @Setter
    private volatile boolean forcePermanentFailures = false;
}