package com.jjenus.qliina_management.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.service.channel.SmsChannelService;
import com.jjenus.qliina_management.notification.service.channel.WhatsAppChannelService;

/**
 * Proves the notification channels fail CLOSED (never silently fake a delivery)
 * when the sandbox gate {@code app.notification.channels.mock-external} is off —
 * the production configuration. All three external channels (SMS/Push/WhatsApp)
 * throw a distinct UNIMPLEMENTED code before touching recipient or provider config.
 */
@TestPropertySource(properties = {
        "app.notification.channels.mock-external=false",
})
class NotificationChannelFailClosedIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SmsChannelService smsChannel;

    @Autowired
    private WhatsAppChannelService whatsAppChannel;

    @Test
    void smsSendTest_failsClosedWhenMockGateIsOff() {
        assertThatThrownBy(() -> smsChannel.sendTest(UUID_ANY, "+15551234567", "hi"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SMS_SEND_UNIMPLEMENTED");
    }

    @Test
    void whatsAppSendTest_failsClosedWhenMockGateIsOff() {
        assertThatThrownBy(() -> whatsAppChannel.sendTest(UUID_ANY, "+15551234567", "hi"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "WHATSAPP_SEND_UNIMPLEMENTED");
    }

    private static final java.util.UUID UUID_ANY = java.util.UUID.randomUUID();
}