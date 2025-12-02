package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventDTO {
    private UUID id;
    private String type;
    private String status;
    private String description;
    private LocalDateTime timestamp;
    private UUID userId;
    private String userName;
    private Map<String, Object> metadata;
}
