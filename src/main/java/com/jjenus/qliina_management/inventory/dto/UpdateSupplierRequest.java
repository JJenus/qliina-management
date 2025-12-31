package com.jjenus.qliina_management.inventory.dto;

import lombok.Data;
import com.jjenus.qliina_management.common.AddressDTO;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateSupplierRequest {
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private AddressDTO address;
    private String paymentTerms;
    private Integer leadTimeDays;
    private List<String> categories;
    private BigDecimal rating;
    private Boolean isActive;
    private BigDecimal minimumOrderAmount;
    private BigDecimal shippingCost;
    private String taxId;
    private String website;
    private String notes;
}
