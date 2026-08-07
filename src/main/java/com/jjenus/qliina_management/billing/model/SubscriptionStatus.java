package com.jjenus.qliina_management.billing.model;

/**
 * Subscription lifecycle state machine (MD §6).
 * {@link #CANCELED} is terminal — reactivation creates a new subscription.
 */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED
}
