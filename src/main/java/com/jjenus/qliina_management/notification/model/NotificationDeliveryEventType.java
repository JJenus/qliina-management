package com.jjenus.qliina_management.notification.model;

/**
 * Append-only delivery event stream (doc §2 delivery_events). One delivery can
 * accumulate multiple events over time: sent -> delivered -> opened -> clicked,
 * or a terminal failed.
 */
public enum NotificationDeliveryEventType {
    SENT, DELIVERED, OPENED, CLICKED, FAILED
}