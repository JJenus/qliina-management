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
public class DefectDTO {
    private UUID id;
    private String type;
    private String severity;
    private String description;
    private List<String> images;
    private String reportedBy;
    private LocalDateTime reportedAt;
    private String status;
    private String resolution;
    private Double compensation;
    private String compensationType;
}
