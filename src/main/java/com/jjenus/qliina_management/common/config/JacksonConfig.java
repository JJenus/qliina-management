package com.jjenus.qliina_management.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jjenus.qliina_management.common.TimezoneContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Serialize all LocalDateTime fields as UTC ISO-8601 strings so JS
        // engines on any client timezone parse elapsed-time correctly.
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Registered after JavaTimeModule so it wins for LocalDateTime.
        // When TimezoneContext carries the business timezone, timestamps are
        // emitted with that zone's offset (e.g. 2026-08-07T08:05:00+01:00) so
        // clients in any timezone reconstruct the correct instant. Falls back
        // to naive ISO output (no offset) outside business-scoped requests.
        SimpleModule zoneModule = new SimpleModule();
        zoneModule.addSerializer(LocalDateTime.class, new BusinessZoneLocalDateTimeSerializer());
        mapper.registerModule(zoneModule);

        return mapper;
    }

    private static class BusinessZoneLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            ZoneId zone = TimezoneContext.getZone();
            if (zone == null) {
                gen.writeString(ISO_LOCAL_DATE_TIME.format(value));
                return;
            }
            OffsetDateTime odt = value.atZone(zone).toOffsetDateTime();
            gen.writeString(odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
