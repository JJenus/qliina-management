package com.jjenus.qliina_management.identity.model;

import com.jjenus.qliina_management.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Operational configuration for a business tenant.
 *
 * Stores settings only (tax rates, order prefixes, loyalty rules, notification
 * flags). Identity and profile data (name, contact info, status, plan) now
 * lives in com.jjenus.qliina_management.business.model.Business.
 *
 * One row per business. Created lazily when BusinessConfigService.getConfig()
 * is first called for a new businessId.
 */
@Entity
@Table(name = "business_config", indexes = {
    @Index(name = "idx_config_business_id", columnList = "business_id", unique = true)
})
@Getter @Setter
public class BusinessConfig extends BaseEntity {

    /** References com.jjenus.qliina_management.business.model.Business.getId(). */
    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "currency")
    private String currency = "USD";

    @Column(name = "timezone")
    private String timezone = "America/New_York";

    @Column(name = "date_format")
    private String dateFormat = "MM/dd/yyyy";

    @Column(name = "receipt_prefix")
    private String receiptPrefix;

    @Column(name = "invoice_prefix")
    private String invoicePrefix;

    @Column(name = "order_prefix")
    private String orderPrefix;

    @Column(name = "loyalty_points_per_dollar")
    private Integer loyaltyPointsPerDollar = 10;

    @Column(name = "min_redeemable_points")
    private Integer minRedeemablePoints = 100;

    @Column(name = "auto_archive_days")
    private Integer autoArchiveDays = 90;

    @Column(name = "allow_negative_inventory")
    private Boolean allowNegativeInventory = false;

    @Column(name = "require_quality_check")
    private Boolean requireQualityCheck = true;

    @Embedded
    private NotificationSettings notificationSettings;

    /** Notification preference flags stored as flat columns in business_config. */
    @Embeddable
    @Getter @Setter
    public static class NotificationSettings {
        @Column(name = "email_notifications")      private Boolean emailNotifications = true;
        @Column(name = "sms_notifications")        private Boolean smsNotifications = false;
        @Column(name = "whatsapp_notifications")   private Boolean whatsappNotifications = false;
        @Column(name = "order_confirmation")       private Boolean orderConfirmation = true;
        @Column(name = "order_ready")              private Boolean orderReady = true;
        @Column(name = "payment_receipt")          private Boolean paymentReceipt = true;
        /** Hours before due date to send a reminder. */
        @Column(name = "reminder_before_due")      private Integer reminderBeforeDue = 24;
    }
}
