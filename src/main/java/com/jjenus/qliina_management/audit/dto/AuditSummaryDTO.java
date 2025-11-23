package com.jjenus.qliina_management.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder; 
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSummaryDTO {
    private Long totalEvents;
    private Long criticalEvents;
    private Long warningEvents;
    private List<ActivitySummaryDTO> topActions;
    private List<UserActivityDTO> topUsers;
    private Map<String, Long> eventsByCategory;
    private Map<String, Long> eventsBySeverity;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivitySummaryDTO {
        private String action;
        private Long count;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityDTO {
        private UUID userId;
        private String userName;
        private Long eventCount;
    }
}
