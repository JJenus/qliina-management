// ./src/main/java/com/jjenus/qliina_management/order/dto/OrderDiscrepancyDTO.java
package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDiscrepancyDTO {
    private UUID id;
    private UUID orderId;
    private String orderNumber;
    private UUID orderItemId;
    private String itemCode;          // resolved item barcode for display, null if order-level
    private UUID reportedBy;
    private String reportedByName;
    private String type;
    private String description;
    private String status;
    private UUID handledBy;
    private String handledByName;
    private String handlingNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
