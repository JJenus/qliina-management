package com.jjenus.qliina_management.billing.model;

/**
 * Invoice lifecycle (MD §2 invoices).
 */
public enum InvoiceStatus {
    DRAFT,
    OPEN,
    PAID,
    FAILED
}
