// ./src/main/java/com/jjenus/qliina_management/inventory/dto/LogStockUsageRequest.java
package com.jjenus.qliina_management.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Worker logging consumables used while processing an order
 * (e.g. detergent, softener, hangers).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStockUsageRequest {

    @NotNull(message = "Inventory item is required")
    private UUID itemId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be positive")
    private BigDecimal quantity;

    /** Optional order the usage is attributed to. */
    private UUID orderId;

    /** Optional specific order item. */
    private UUID orderItemId;

    private String notes;
}
