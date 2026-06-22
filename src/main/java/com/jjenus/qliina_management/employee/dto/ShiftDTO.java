package com.jjenus.qliina_management.employee.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDTO {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID shopId;
    private String shopName;
    private LocalDate date;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    @JsonProperty("clockIn")
    private LocalDateTime actualStart;
    @JsonProperty("clockOut")
    private LocalDateTime actualEnd;
    private LocalDateTime breakStart;
    private LocalDateTime breakEnd;
    @JsonProperty("breakMinutes")
    private Integer totalBreakMinutes;
    @JsonProperty("workedMinutes")
    private Integer totalWorkMinutes;
    private Integer overtimeMinutes;
    private String status;
    @JsonProperty("suspendMinutes")
    private Integer totalSuspendMinutes;
    private boolean autoClosed;
    private String notes;
}
