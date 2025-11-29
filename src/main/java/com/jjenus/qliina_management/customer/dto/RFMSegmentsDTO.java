package com.jjenus.qliina_management.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RFMSegmentsDTO {
    private List<SegmentDTO> segments;
    private Map<String, List<DistributionDTO>> distribution;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentDTO {
        private String name;
        private Long count;
        private Double percentage;
        private Double totalValue;
        private Double averageValue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributionDTO {
        private String bucket;
        private Long count;
    }
}
