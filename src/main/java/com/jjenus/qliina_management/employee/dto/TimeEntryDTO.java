package com.jjenus.qliina_management.employee.dto;

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
public class TimeEntryDTO {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID shopId;
    private String shopName;
    private String eventType;
    private LocalDateTime timestamp;
    private DeviceInfoDTO deviceInfo;
    private String notes;
    
    @Data
    @Builder
    @NoArgsConstructor
@AllArgsConstructor
    public static class DeviceInfoDTO {
        private String deviceId;
        private String ipAddress;
        private String location;
    }
}
