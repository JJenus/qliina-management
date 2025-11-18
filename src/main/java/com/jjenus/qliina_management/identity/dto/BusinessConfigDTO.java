package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfigDTO {
    private UUID businessId;
    private String businessName;
    private BigDecimal taxRate;
    private String currency;
    private String timezone;
    private String dateFormat;
    private String receiptPrefix;
    private String invoicePrefix;
    private String orderPrefix;
    private Integer loyaltyPointsPerDollar;
    private Integer minRedeemablePoints;
    private Integer autoArchiveDays;
    private Boolean allowNegativeInventory;
    private Boolean requireQualityCheck;
    private NotificationSettingsDTO notificationSettings;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettingsDTO {
        private Boolean emailNotifications;
        private Boolean smsNotifications;
        private Boolean whatsappNotifications;
        private Boolean orderConfirmation;
        private Boolean orderReady;
        private Boolean paymentReceipt;
        private Integer reminderBeforeDue;
    }
}