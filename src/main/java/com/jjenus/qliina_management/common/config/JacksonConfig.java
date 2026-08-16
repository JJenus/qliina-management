package com.jjenus.qliina_management.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jjenus.qliina_management.common.TimezoneContext;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ValueSerializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter ISO_NAIVE = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Spring Boot 4 serializes HTTP responses with Jackson 3 (tools.jackson),
     * built via JsonMapperBuilderCustomizer beans. Register a zone-aware
     * LocalDateTime serializer there so business-scoped requests emit an
     * ISO-8601 offset (e.g. 2026-08-07T08:05:00+01:00) instead of a naive
     * string, letting JS clients on any timezone reconstruct the correct
     * instant for elapsed-time math. Falls back to naive ISO output outside
     * business-scoped requests (no TimezoneContext zone set).
     */
    @Bean
    public JsonMapperBuilderCustomizer businessZoneLocalDateTimeCustomizer() {
        return builder -> {
            tools.jackson.databind.module.SimpleModule zoneModule = new tools.jackson.databind.module.SimpleModule();
            zoneModule.addSerializer(LocalDateTime.class, new Jackson3ZoneLocalDateTimeSerializer());
            builder.addModule(zoneModule);
        };
    }

    private static class Jackson3ZoneLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, tools.jackson.core.JsonGenerator gen,
                              tools.jackson.databind.SerializationContext serializers)
                throws tools.jackson.core.JacksonException {
            ZoneId zone = TimezoneContext.getZone();
            if (zone == null) {
                gen.writeString(ISO_NAIVE.format(value));
                return;
            }
            OffsetDateTime odt = value.atZone(zone).toOffsetDateTime();
            gen.writeString(odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    /**
     * Jackson 2 mapper kept for internal consumers that inject the legacy
     * com.fasterxml.jackson ObjectMapper (audit JSON storage etc.).
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        SimpleModule zoneModule = new SimpleModule();
        zoneModule.addSerializer(LocalDateTime.class, new Jackson2ZoneLocalDateTimeSerializer());
        mapper.registerModule(zoneModule);

        return mapper;
    }

    private static class Jackson2ZoneLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, com.fasterxml.jackson.core.JsonGenerator gen,
                              SerializerProvider serializers) throws IOException {
            ZoneId zone = TimezoneContext.getZone();
            if (zone == null) {
                gen.writeString(ISO_NAIVE.format(value));
                return;
            }
            OffsetDateTime odt = value.atZone(zone).toOffsetDateTime();
            gen.writeString(odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
