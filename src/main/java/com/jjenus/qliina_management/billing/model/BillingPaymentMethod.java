package com.jjenus.qliina_management.billing.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A stored billing method for a business (MD §2 payment_methods).
 * Distinct from the order-payment module's shop `payment_methods`.
 */
@Entity
@Table(name = "billing_payment_methods", indexes = {
    @Index(name = "idx_billing_payment_methods_business", columnList = "business_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentMethod extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PaymentMethodType type;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "gateway_customer_id", length = 128)
    private String gatewayCustomerId;

    @Column(name = "gateway_method_id", length = 128)
    private String gatewayMethodId;

    @Column(name = "label", length = 120)
    private String label;
}
