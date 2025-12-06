package com.jjenus.qliina_management.inventory.model;

import com.jjenus.qliina_management.common.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "suppliers", indexes = {
    @Index(name = "idx_supplier_name", columnList = "name"),
    @Index(name = "idx_supplier_email", columnList = "email")
})
@Getter
@Setter
public class Supplier extends BaseTenantEntity {
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "contact_person")
    private String contactPerson;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "phone")
    private String phone;
    
    @Embedded
    private Address address;
    
    @Column(name = "payment_terms")
    private String paymentTerms;
    
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;
    
    @ElementCollection
    @CollectionTable(name = "supplier_categories", joinColumns = @JoinColumn(name = "supplier_id"))
    @Column(name = "category")
    private List<String> categories = new ArrayList<>();
    
    @Column(name = "rating", precision = 2, scale = 1)
    private BigDecimal rating;
    
    @Column(name = "tax_id")
    private String taxId;
    
    @Column(name = "website")
    private String website;
    
    @Column(name = "notes")
    private String notes;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "minimum_order_amount", precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;
    
    @Column(name = "shipping_cost", precision = 10, scale = 2)
    private BigDecimal shippingCost;
    
    @Embeddable
    @Getter
    @Setter
    public static class Address {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }
}
