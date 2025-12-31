package com.jjenus.qliina_management.quality.dto;

import lombok.Data;

@Data
public class UpdateDefectRequest {
    private String status;
    private String resolution;
    private Double compensation;
    private String compensationType;
    private String notes;
}
