package com.jjenus.qliina_management.identity.service;

import com.jjenus.qliina_management.identity.dto.BusinessConfigDTO;
import com.jjenus.qliina_management.identity.model.BusinessConfig;
import com.jjenus.qliina_management.identity.repository.BusinessConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for per-business operational configuration.
 *
 * Rows are created lazily on first read. businessName has been removed —
 * update the display name via com.jjenus.qliina_management.business.service.BusinessService.updateBusiness.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessConfigService {

    private final BusinessConfigRepository configRepository;

    @Transactional(readOnly = true)
    public BusinessConfigDTO getConfig(UUID businessId) {
        return mapToDTO(configRepository.findByBusinessId(businessId)
                .orElseGet(() -> createDefaultConfig(businessId)));
    }

    @Transactional
    public BusinessConfigDTO updateConfig(UUID businessId, BusinessConfigDTO dto) {
        BusinessConfig c = configRepository.findByBusinessId(businessId).orElse(new BusinessConfig());
        c.setBusinessId(businessId);
        c.setTaxRate(dto.getTaxRate());                         c.setCurrency(dto.getCurrency());
        c.setTimezone(dto.getTimezone());                       c.setDateFormat(dto.getDateFormat());
        c.setReceiptPrefix(dto.getReceiptPrefix());             c.setInvoicePrefix(dto.getInvoicePrefix());
        c.setOrderPrefix(dto.getOrderPrefix());
        c.setLoyaltyPointsPerDollar(dto.getLoyaltyPointsPerDollar());
        c.setMinRedeemablePoints(dto.getMinRedeemablePoints()); c.setAutoArchiveDays(dto.getAutoArchiveDays());
        c.setAllowNegativeInventory(dto.getAllowNegativeInventory());
        c.setRequireQualityCheck(dto.getRequireQualityCheck());
        if (dto.getNotificationSettings() != null) {
            BusinessConfig.NotificationSettings s = new BusinessConfig.NotificationSettings();
            s.setEmailNotifications(dto.getNotificationSettings().getEmailNotifications());
            s.setSmsNotifications(dto.getNotificationSettings().getSmsNotifications());
            s.setWhatsappNotifications(dto.getNotificationSettings().getWhatsappNotifications());
            s.setOrderConfirmation(dto.getNotificationSettings().getOrderConfirmation());
            s.setOrderReady(dto.getNotificationSettings().getOrderReady());
            s.setPaymentReceipt(dto.getNotificationSettings().getPaymentReceipt());
            s.setReminderBeforeDue(dto.getNotificationSettings().getReminderBeforeDue());
            c.setNotificationSettings(s);
        }
        return mapToDTO(configRepository.save(c));
    }

    @Transactional
    public void resetToDefaults(UUID businessId) {
        BusinessConfig c = configRepository.findByBusinessId(businessId).orElse(new BusinessConfig());
        c.setBusinessId(businessId);
        applyDefaults(c);
        configRepository.save(c);
    }

    private BusinessConfig createDefaultConfig(UUID businessId) {
        BusinessConfig c = new BusinessConfig();
        c.setBusinessId(businessId);
        applyDefaults(c);
        return configRepository.save(c);
    }

    private void applyDefaults(BusinessConfig c) {
        c.setTaxRate(BigDecimal.ZERO); c.setCurrency("USD");
        c.setTimezone("America/New_York"); c.setDateFormat("MM/dd/yyyy");
        c.setReceiptPrefix("RCPT"); c.setInvoicePrefix("INV"); c.setOrderPrefix("ORD");
        c.setLoyaltyPointsPerDollar(10); c.setMinRedeemablePoints(100);
        c.setAutoArchiveDays(90); c.setAllowNegativeInventory(false); c.setRequireQualityCheck(true);
        BusinessConfig.NotificationSettings s = new BusinessConfig.NotificationSettings();
        s.setEmailNotifications(true); s.setSmsNotifications(false); s.setWhatsappNotifications(false);
        s.setOrderConfirmation(true); s.setOrderReady(true); s.setPaymentReceipt(true); s.setReminderBeforeDue(24);
        c.setNotificationSettings(s);
    }

    private BusinessConfigDTO mapToDTO(BusinessConfig c) {
        BusinessConfigDTO.BusinessConfigDTOBuilder b = BusinessConfigDTO.builder()
                .businessId(c.getBusinessId()).taxRate(c.getTaxRate()).currency(c.getCurrency())
                .timezone(c.getTimezone()).dateFormat(c.getDateFormat())
                .receiptPrefix(c.getReceiptPrefix()).invoicePrefix(c.getInvoicePrefix()).orderPrefix(c.getOrderPrefix())
                .loyaltyPointsPerDollar(c.getLoyaltyPointsPerDollar()).minRedeemablePoints(c.getMinRedeemablePoints())
                .autoArchiveDays(c.getAutoArchiveDays()).allowNegativeInventory(c.getAllowNegativeInventory())
                .requireQualityCheck(c.getRequireQualityCheck());
        if (c.getNotificationSettings() != null) {
            b.notificationSettings(BusinessConfigDTO.NotificationSettingsDTO.builder()
                    .emailNotifications(c.getNotificationSettings().getEmailNotifications())
                    .smsNotifications(c.getNotificationSettings().getSmsNotifications())
                    .whatsappNotifications(c.getNotificationSettings().getWhatsappNotifications())
                    .orderConfirmation(c.getNotificationSettings().getOrderConfirmation())
                    .orderReady(c.getNotificationSettings().getOrderReady())
                    .paymentReceipt(c.getNotificationSettings().getPaymentReceipt())
                    .reminderBeforeDue(c.getNotificationSettings().getReminderBeforeDue()).build());
        }
        return b.build();
    }
}
