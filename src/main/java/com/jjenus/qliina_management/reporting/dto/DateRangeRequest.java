package com.jjenus.qliina_management.reporting.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DateRangeRequest {
    @NotNull(message = "startDate is required")
    private LocalDate startDate;
    @NotNull(message = "endDate is required")
    private LocalDate endDate;
}
