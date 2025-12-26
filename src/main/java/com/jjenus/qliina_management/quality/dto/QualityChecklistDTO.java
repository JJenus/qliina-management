package com.jjenus.qliina_management.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityChecklistDTO {
    private UUID id;
    private String name;
    private UUID serviceTypeId;
    private UUID garmentTypeId;
    private List<ItemDTO> items;
    private Boolean isActive;
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO {
        private UUID id;
        private String description;
        private Boolean required;
        private Integer order;
        private String failureSeverity;
    }
}
