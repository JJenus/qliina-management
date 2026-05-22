package com.jjenus.qliina_management.identity.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for BusinessConfig.
 *
 * Note: businessName has been removed. Business identity data (name, email,
 * logo, status) is managed via GET/PUT /api/v1/businesses/{businessId}
 * using com.jjenus.qliina_management.business.dto.BusinessDTO.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessConfigDTO {
    private UUID       businessId;
    private BigDecimal taxRate;
    private String     currency;
    private String     currencySymbol;
    private String     currencyLocale;
    private String     timezone;
    private String     dateFormat;
    private String     receiptPrefix;
    private String     invoicePrefix;
    private String     orderPrefix;
    private Integer    loyaltyPointsPerDollar;
    private Integer    minRedeemablePoints;
    private Integer    autoArchiveDays;
    private Boolean    allowNegativeInventory;
    private Boolean    requireQualityCheck;
    private NotificationSettingsDTO notificationSettings;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
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
