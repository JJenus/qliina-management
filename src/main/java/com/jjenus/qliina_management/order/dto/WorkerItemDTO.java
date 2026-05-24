// ./src/main/java/com/jjenus/qliina_management/order/dto/WorkerItemDTO.java
package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerItemDTO {
    
    private UUID id;
    private String shortId;        // Scannable short ID (e.g., "A7K3M9X2")
    private UUID orderId;
    private String orderNumber;
    private String customerName;
    private String serviceType;
    private String garmentType;
    private String description;
    private Integer quantity;
    private BigDecimal weight;
    private String status;
    private String priority;
    private String specialInstructions;
    private List<String> images;
    private LocalDateTime receivedAt;
    private LocalDateTime promisedDate;
    
    /** Individual unit barcodes for multi-quantity items (null for single-unit items). */
    private List<String> unitBarcodes;

    /** Total number of units (null for single-unit items). */
    private Integer totalUnits;

    // Actions available to the current worker
    private List<String> availableActions; // e.g., ["START", "COMPLETE"]
    
    // QC-related (shown when item needs QC)
    private boolean needsQualityCheck;
    private QualityCheckSummaryDTO lastQualityCheck;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityCheckSummaryDTO {
        private UUID checkId;
        private String status;
        private LocalDateTime checkedAt;
        private Double score;
    }
}