package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertDTO {
    private UUID id;
    private UUID shopId;
    private String shopName;
    private UUID itemId;
    private String itemName;
    private String itemSku;
    private BigDecimal currentStock;
    private Integer reorderLevel;
    private String status;
    private Integer suggestedOrder;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
    private String message;
    private String severity;
}
