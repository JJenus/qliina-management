package com.jjenus.qliina_management.notification.model;

/**
 * Outbox row lifecycle (doc §5). PENDING rows are claimed by a worker under a
 * lease; PROCESSED on success; DEAD (DLQ) once the message-level retry budget is
 * exhausted so it can be inspected manually instead of looping forever.
 */
public enum NotificationOutboxStatus {
    PENDING, PROCESSED, DEAD
}