package com.jjenus.qliina_management.notification.model;

/**
 * Lifecycle of a single channel delivery (doc §2 deliveries, §4 retry).
 */
public enum NotificationDeliveryStatus {
    PENDING, SENT, FAILED, EXHAUSTED
}