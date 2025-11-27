package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
public class CustomerAddress extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @Column(name = "type")
    private String type;
    
    @Column(name = "address_line1", nullable = false)
    private String addressLine1;
    
    @Column(name = "address_line2")
    private String addressLine2;
    
    @Column(name = "city", nullable = false)
    private String city;
    
    @Column(name = "state")
    private String state;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    @Column(name = "country")
    private String country = "USA";
    
    @Column(name = "is_default")
    private boolean isDefault = false;
    
    @Column(name = "instructions")
    private String instructions;
}
