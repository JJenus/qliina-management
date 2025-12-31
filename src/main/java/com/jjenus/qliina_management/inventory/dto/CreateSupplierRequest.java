package com.jjenus.qliina_management.inventory.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateSupplierRequest {
    @NotBlank(message = "Supplier name is required")
    private String name;
    
    private String contactPerson;
    
    @Email(message = "Invalid email format")
    private String email;
    
    private String phone;
    
    private AddressDTO address;
    
    private String paymentTerms;
    
    private Integer leadTimeDays;
    
    private List<String> categories;
    
    private BigDecimal minimumOrderAmount;
    
    private BigDecimal shippingCost;
    
    private String taxId;
    
    private String website;
    
    private String notes;
}
