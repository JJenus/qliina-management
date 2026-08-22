// ./src/main/java/com/jjenus/qliina_management/inventory/dto/CreateStockRequestRequest.java
package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockRequestRequest {

    @NotNull(message = "Inventory item is required")
    private UUID itemId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be positive")
    private BigDecimal quantity;

    /** LOW | NORMAL | HIGH (default NORMAL) */
    @NotBlank(message = "Urgency is required")
    private String urgency;

    private String notes;
}
