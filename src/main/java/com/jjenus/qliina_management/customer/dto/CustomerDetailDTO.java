package com.jjenus.qliina_management.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerDetailDTO extends CustomerSummaryDTO {
    private List<AddressDTO> addresses;
    private PreferencesDTO preferences;
    private List<NoteDTO> notes;
    private LoyaltyInfoDTO loyalty;
    private RFMInfoDTO rfm;
    private MetadataDTO metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDTO {
        private UUID id;
        private String type;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private Boolean isDefault;
        private String instructions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferencesDTO {
        private List<String> fabricCare;
        private String deliveryInstructions;
        private Boolean notifyViaSms;
        private Boolean notifyViaEmail;
        private String preferredPaymentMethod;
        private UUID preferredShopId;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteDTO {
        private UUID id;
        private String content;
        private String createdBy;
        private LocalDateTime createdAt;
        private String type;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoyaltyInfoDTO {
        private Integer points;
        private Integer lifetimePoints;
        private String tier;
        private Integer pointsToNextTier;
        private String referralCode;
        private UUID referredBy;
        private Integer referralCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RFMInfoDTO {
        private Integer recency;
        private Integer frequency;
        private Double monetary;
        private String segment;
        private Integer score;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDTO {
        private UUID createdBy;
        private LocalDateTime createdAt;
        private UUID updatedBy;
        private LocalDateTime updatedAt;
    }
}
