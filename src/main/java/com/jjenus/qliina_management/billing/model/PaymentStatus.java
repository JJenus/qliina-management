package com.jjenus.qliina_management.billing.model;

/**
 * Payment lifecycle (MD §2 payments).
 */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    FAILED,
    REFUNDED,
    DISPUTED
}
