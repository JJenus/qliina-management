package com.jjenus.qliina_management.identity.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.dto.BusinessConfigDTO;
import com.jjenus.qliina_management.identity.model.BusinessConfig;
import com.jjenus.qliina_management.identity.repository.BusinessConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessConfigService {
    
    private final BusinessConfigRepository configRepository;
    
    @Transactional(readOnly = true)
    public BusinessConfigDTO getConfig(UUID businessId) {
        BusinessConfig config = configRepository.findByBusinessId(businessId)
            .orElseGet(() -> createDefaultConfig(businessId));
        return mapToDTO(config);
    }
    
    @Transactional
    public BusinessConfigDTO updateConfig(UUID businessId, BusinessConfigDTO dto) {
        BusinessConfig config = configRepository.findByBusinessId(businessId)
            .orElse(new BusinessConfig());
        
        config.setBusinessId(businessId);
        config.setBusinessName(dto.getBusinessName());
        config.setTaxRate(dto.getTaxRate());
        config.setCurrency(dto.getCurrency());
        config.setTimezone(dto.getTimezone());
        config.setDateFormat(dto.getDateFormat());
        config.setReceiptPrefix(dto.getReceiptPrefix());
        config.setInvoicePrefix(dto.getInvoicePrefix());
        config.setOrderPrefix(dto.getOrderPrefix());
        config.setLoyaltyPointsPerDollar(dto.getLoyaltyPointsPerDollar());
        config.setMinRedeemablePoints(dto.getMinRedeemablePoints());
        config.setAutoArchiveDays(dto.getAutoArchiveDays());
        config.setAllowNegativeInventory(dto.getAllowNegativeInventory());
        config.setRequireQualityCheck(dto.getRequireQualityCheck());
        
        if (dto.getNotificationSettings() != null) {
            BusinessConfig.NotificationSettings settings = new BusinessConfig.NotificationSettings();
            settings.setEmailNotifications(dto.getNotificationSettings().getEmailNotifications());
            settings.setSmsNotifications(dto.getNotificationSettings().getSmsNotifications());
            settings.setWhatsappNotifications(dto.getNotificationSettings().getWhatsappNotifications());
            settings.setOrderConfirmation(dto.getNotificationSettings().getOrderConfirmation());
            settings.setOrderReady(dto.getNotificationSettings().getOrderReady());
            settings.setPaymentReceipt(dto.getNotificationSettings().getPaymentReceipt());
            settings.setReminderBeforeDue(dto.getNotificationSettings().getReminderBeforeDue());
            config.setNotificationSettings(settings);
        }
        
        config = configRepository.save(config);
        return mapToDTO(config);
    }
    
    @Transactional
    public void resetToDefaults(UUID businessId) {
        BusinessConfig config = configRepository.findByBusinessId(businessId)
            .orElse(new BusinessConfig());
        
        config.setBusinessId(businessId);
        config.setTaxRate(new BigDecimal("0.0"));
        config.setCurrency("USD");
        config.setTimezone("America/New_York");
        config.setDateFormat("MM/dd/yyyy");
        config.setReceiptPrefix("RCPT");
        config.setInvoicePrefix("INV");
        config.setOrderPrefix("ORD");
        config.setLoyaltyPointsPerDollar(10);
        config.setMinRedeemablePoints(100);
        config.setAutoArchiveDays(90);
        config.setAllowNegativeInventory(false);
        config.setRequireQualityCheck(true);
        
        BusinessConfig.NotificationSettings settings = new BusinessConfig.NotificationSettings();
        settings.setEmailNotifications(true);
        settings.setSmsNotifications(false);
        settings.setWhatsappNotifications(false);
        settings.setOrderConfirmation(true);
        settings.setOrderReady(true);
        settings.setPaymentReceipt(true);
        settings.setReminderBeforeDue(24);
        config.setNotificationSettings(settings);
        
        configRepository.save(config);
    }
    
    private BusinessConfig createDefaultConfig(UUID businessId) {
        BusinessConfig config = new BusinessConfig();
        config.setBusinessId(businessId);
        config.setTaxRate(BigDecimal.ZERO);
        config.setCurrency("USD");
        config.setTimezone("America/New_York");
        config.setDateFormat("MM/dd/yyyy");
        config.setReceiptPrefix("RCPT");
        config.setInvoicePrefix("INV");
        config.setOrderPrefix("ORD");
        config.setLoyaltyPointsPerDollar(10);
        config.setMinRedeemablePoints(100);
        config.setAutoArchiveDays(90);
        config.setAllowNegativeInventory(false);
        config.setRequireQualityCheck(true);
        
        BusinessConfig.NotificationSettings settings = new BusinessConfig.NotificationSettings();
        settings.setEmailNotifications(true);
        settings.setSmsNotifications(false);
        settings.setWhatsappNotifications(false);
        settings.setOrderConfirmation(true);
        settings.setOrderReady(true);
        settings.setPaymentReceipt(true);
        settings.setReminderBeforeDue(24);
        config.setNotificationSettings(settings);
        
        return configRepository.save(config);
    }
    
    private BusinessConfigDTO mapToDTO(BusinessConfig config) {
        BusinessConfigDTO.BusinessConfigDTOBuilder builder = BusinessConfigDTO.builder()
            .businessId(config.getBusinessId())
            .businessName(config.getBusinessName())
            .taxRate(config.getTaxRate())
            .currency(config.getCurrency())
            .timezone(config.getTimezone())
            .dateFormat(config.getDateFormat())
            .receiptPrefix(config.getReceiptPrefix())
            .invoicePrefix(config.getInvoicePrefix())
            .orderPrefix(config.getOrderPrefix())
            .loyaltyPointsPerDollar(config.getLoyaltyPointsPerDollar())
            .minRedeemablePoints(config.getMinRedeemablePoints())
            .autoArchiveDays(config.getAutoArchiveDays())
            .allowNegativeInventory(config.getAllowNegativeInventory())
            .requireQualityCheck(config.getRequireQualityCheck());
        
        if (config.getNotificationSettings() != null) {
            builder.notificationSettings(BusinessConfigDTO.NotificationSettingsDTO.builder()
                .emailNotifications(config.getNotificationSettings().getEmailNotifications())
                .smsNotifications(config.getNotificationSettings().getSmsNotifications())
                .whatsappNotifications(config.getNotificationSettings().getWhatsappNotifications())
                .orderConfirmation(config.getNotificationSettings().getOrderConfirmation())
                .orderReady(config.getNotificationSettings().getOrderReady())
                .paymentReceipt(config.getNotificationSettings().getPaymentReceipt())
                .reminderBeforeDue(config.getNotificationSettings().getReminderBeforeDue())
                .build());
        }
        
        return builder.build();
    }
}