package com.jjenus.qliina_management.billing.model;

/**
 * Plan lifecycle — never hard-deleted once referenced (MD §5).
 */
public enum PlanStatus {
    ACTIVE,
    DEPRECATED,
    ARCHIVED
}
