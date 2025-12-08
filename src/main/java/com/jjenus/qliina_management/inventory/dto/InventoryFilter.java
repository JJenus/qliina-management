package com.jjenus.qliina_management.inventory.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class InventoryFilter {
    private String search;
    private String category;
    private UUID supplierId;
    private Boolean isActive;
    private Boolean lowStockOnly;
    private Boolean criticalStockOnly;
}
