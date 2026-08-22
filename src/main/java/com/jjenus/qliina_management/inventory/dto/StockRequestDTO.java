// ./src/main/java/com/jjenus/qliina_management/inventory/dto/StockRequestDTO.java
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
public class StockRequestDTO {
    private UUID id;
    private UUID shopId;
    private UUID itemId;
    private String itemName;
    private String unit;
    private BigDecimal quantity;
    private String urgency;
    private String notes;
    private String status;
    private UUID requestedBy;
    private String requestedByName;
    private UUID reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String resolutionNote;
    private LocalDateTime createdAt;
}
