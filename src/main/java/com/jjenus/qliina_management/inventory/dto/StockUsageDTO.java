// ./src/main/java/com/jjenus/qliina_management/inventory/dto/StockUsageDTO.java
package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single stock-usage entry as seen by the worker who logged it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockUsageDTO {
    private UUID id;
    private UUID itemId;
    private String itemName;
    private String unit;
    private BigDecimal quantity;
    private String orderNumber;     // resolved for display, null if not tied to an order
    private String notes;
    private LocalDateTime transactionDate;
}
