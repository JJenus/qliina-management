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
public class StockTransactionDTO {
    private UUID id;
    private UUID shopId;
    private String shopName;
    private UUID itemId;
    private String itemName;
    private String itemSku;
    private BigDecimal quantity;
    private String type;
    private String reason;
    private String reference;
    private String notes;
    private String performedBy;
    private LocalDateTime transactionDate;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
}
