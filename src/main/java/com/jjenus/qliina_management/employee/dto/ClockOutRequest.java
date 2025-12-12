package com.jjenus.qliina_management.employee.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ClockOutRequest {
    private UUID shiftId;
    private String notes;
}
