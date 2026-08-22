// ./src/main/java/com/jjenus/qliina_management/inventory/dto/WorkerStockItemDTO.java
package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight inventory item for worker-facing stock usage / supply requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerStockItemDTO {
    private UUID id;
    private String name;
    private String sku;
    private String category;      // InventoryItem.ItemCategory
    private String unit;          // InventoryItem.UnitOfMeasure
    private BigDecimal availableQuantity; // in the worker's shop (null if untracked)
}
