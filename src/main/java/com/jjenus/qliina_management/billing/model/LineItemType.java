package com.jjenus.qliina_management.billing.model;

/**
 * Line-item kinds; proration/credits are appended, never mutated (MD §7).
 */
public enum LineItemType {
    SUBSCRIPTION,
    PRORATION_CREDIT,
    PRORATION_CHARGE,
    ONE_OFF,
    TAX
}
