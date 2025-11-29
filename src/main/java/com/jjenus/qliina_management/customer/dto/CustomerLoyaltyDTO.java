package com.jjenus.qliina_management.customer.dto;

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
public class CustomerLoyaltyDTO {
    private UUID customerId;
    private String customerName;
    private Integer currentPoints;
    private Integer lifetimePoints;
    private TierInfoDTO tier;
    private List<PointsHistoryDTO> pointsHistory;
    private List<AvailableRewardDTO> availableRewards;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierInfoDTO {
        private String name;
        private Integer level;
        private List<String> benefits;
        private Integer pointsRequired;
        private Double progress;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointsHistoryDTO {
        private UUID id;
        private LocalDateTime date;
        private Integer points;
        private Integer balance;
        private String source;
        private UUID sourceId;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableRewardDTO {
        private UUID id;
        private String name;
        private Integer pointsCost;
        private String description;
        private LocalDateTime expiresAt;
    }
}
