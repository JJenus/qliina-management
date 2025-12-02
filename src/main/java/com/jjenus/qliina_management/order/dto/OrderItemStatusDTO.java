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
public class OrderItemStatusDTO {
    private UUID itemId;
    private String previousStatus;
    private String currentStatus;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String notes;
}
