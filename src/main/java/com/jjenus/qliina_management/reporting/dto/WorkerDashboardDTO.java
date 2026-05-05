// ./src/main/java/com/jjenus/qliina_management/reporting/dto/WorkerDashboardDTO.java
package com.jjenus.qliina_management.reporting.dto;

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
public class WorkerDashboardDTO {
    
    private UUID employeeId;
    private String employeeName;
    private String role;
    
    // Today's metrics
    private TodayMetricsDTO todayMetrics;
    
    // Queue summary
    private QueueSummaryDTO queueSummary;
    
    // Quality overview
    private QualityOverviewDTO qualityOverview;
    
    // Recent items (last 20 interacted)
    private List<RecentItemDTO> recentItems;
    
    // Shift info
    private ShiftInfoDTO shiftInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodayMetricsDTO {
        private int itemsProcessed;
        private int itemsPassedQC;
        private int itemsFailedQC;
        private BigDecimal revenueHandled;  // null for non-front-desk roles
        private int onTimeDeliveries;       // null for non-delivery roles
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueSummaryDTO {
        private int pendingItems;       // Items waiting for this role
        private int inProgressItems;    // Items currently being processed
        private String nextStatusLabel; // e.g., "WASHING" → "WASHED"
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityOverviewDTO {
        private Double todayQualityScore;    // 0-100
        private Double weeklyQualityScore;   // 0-100
        private List<String> recentDefectTypes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentItemDTO {
        private UUID itemId;
        private String itemNumber;
        private String orderNumber;
        private String serviceType;
        private String currentStatus;
        private LocalDateTime lastInteraction;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftInfoDTO {
        private boolean isClockedIn;
        private LocalDateTime shiftStart;
        private Long minutesElapsed;
        private Long scheduledMinutes;
        private int breakMinutes;
    }
}