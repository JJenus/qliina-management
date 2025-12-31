package com.jjenus.qliina_management.inventory.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private UUID id;
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private AddressDTO address;
    private String paymentTerms;
    private Integer leadTimeDays;
    private List<String> categories;
    private BigDecimal rating;
    private String taxId;
    private String website;
    private String notes;
    private Boolean isActive;
    private BigDecimal minimumOrderAmount;
    private BigDecimal shippingCost;
    private LocalDateTime createdAt;
}
