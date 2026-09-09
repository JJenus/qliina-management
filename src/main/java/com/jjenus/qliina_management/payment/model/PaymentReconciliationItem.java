package com.jjenus.qliina_management.payment.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Staff-facing queue of provider settlements that arrived with no matching
 * payment record. Unlike every other business-scoped row, this one is OPEN
 * cross-tenant by design: the webhook URL ({@code /api/v1/webhooks/payments/…})
 * carries no business identifier, so the money simply waits here until an admin
 * links it to a business + order (RESOLVED).
 */
@Entity
@Table(name = "payment_reconciliation_items")
@Getter
@Setter
public class PaymentReconciliationItem extends BaseEntity {

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "raw_event", columnDefinition = "text")
    private String rawEvent;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}