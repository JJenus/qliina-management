package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Per-year sequential invoice number counter (MD §2 invoices). Rows are locked
 * with a pessimistic write lock while allocating so numbers never collide.
 */
@Entity
@Table(name = "billing_invoice_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceNumberCounter {

    @Id
    @Column(name = "counter_year", nullable = false)
    private Integer counterYear;

    @Column(name = "next_value", nullable = false)
    private Long nextValue = 1L;
}
