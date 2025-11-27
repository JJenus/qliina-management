package com.jjenus.qliina_management.customer.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_preferences")
@Getter
@Setter
public class CustomerPreferences extends BaseEntity {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;
    
    @Column(name = "fabric_care", columnDefinition = "text[]")
    private List<String> fabricCare = new ArrayList<>();
    
    @Column(name = "delivery_instructions")
    private String deliveryInstructions;
    
    @Column(name = "notify_via_sms")
    private Boolean notifyViaSms = true;
    
    @Column(name = "notify_via_email")
    private Boolean notifyViaEmail = true;
    
    @Column(name = "preferred_payment_method")
    private String preferredPaymentMethod;
    
    @Column(name = "preferred_shop_id")
    private UUID preferredShopId;
}
