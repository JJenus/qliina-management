package com.jjenus.qliina_management.notification.model;

/**
 * Failure classification that drives the retry policy (doc §4). Permanent
 * failures (hard bounce, invalid push token) must NOT be retried — they are
 * exhausted immediately instead of burning attempts.
 */
public enum NotificationDeliveryErrorType {
    TRANSIENT, PERMANENT
}