// ./src/main/java/com/jjenus/qliina_management/reporting/dto/WorkerHistoryDTO.java
package com.jjenus.qliina_management.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Historic daily work stats for the authenticated worker.
 * Returned by GET /api/v1/{businessId}/reports/worker-dashboard/history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerHistoryDTO {

    private int days;
    private List<DailyStatDTO> dailyStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStatDTO {
        private LocalDate date;
        private int itemsProcessed;       // completions that day
        private Double avgMinutesPerItem; // null when no paired start/complete data
    }
}
