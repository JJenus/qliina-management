package com.jjenus.qliina_management.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Embeddable
@Getter
@Setter
public class DeliveryInfo {
    
    @Column(name = "delivery_type")
    private String type;
    
    @Column(name = "delivery_address_id")
    private UUID addressId;
    
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "delivered_by")
    private UUID deliveredBy;
    
    @Column(name = "proof_of_delivery")
    private String proofOfDelivery;
    
    @Column(name = "recipient_name")
    private String recipientName;
    
    @Column(name = "delivery_notes")
    private String notes;
}
