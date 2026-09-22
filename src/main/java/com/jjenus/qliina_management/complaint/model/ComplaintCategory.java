package com.jjenus.qliina_management.complaint.model;

/**
 * Category of a customer complaint / support ticket.
 */
public enum ComplaintCategory {
    SERVICE,   // service experience, wait times, staff
    QUALITY,   // cleaning / finishing defects
    BILLING,   // pricing, invoices, charges
    DELIVERY,  // pickup / delivery issues
    OTHER
}