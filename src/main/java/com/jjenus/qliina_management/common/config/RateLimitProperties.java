package com.jjenus.qliina_management.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Per-IP request budgets enforced by {@link RateLimitFilter}.
 * Defaults are deliberately generous so normal POS usage and the
 * integration suite never trip; tighten per environment if needed.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Applies to every /api/** request. */
    private Limit defaultLimit = new Limit(100, 60);

    /** Stricter budget for /api/v1/auth/** (login brute-force guard). */
    private Limit login = new Limit(20, 10);

    @Data
    public static class Limit {

        /** Maximum burst of requests allowed at once. */
        private long capacity = 100;

        /** Tokens re-added per minute (sustained throughput). */
        private long refillPerMinute = 60;

        public Limit() {
        }

        public Limit(long capacity, long refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }
    }
}