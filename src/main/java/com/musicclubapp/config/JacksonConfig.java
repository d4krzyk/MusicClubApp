package com.musicclubapp.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Ustala, jak daty wychodza z API - i to jest naprawa realnego bledu. */
@Configuration
public class JacksonConfig {

    /** Format wyjsciowy: 2026-08-28T12:00:00Z. */
    private static final DateTimeFormatter UTC_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcDates() {
        return builder -> builder.serializerByType(LocalDateTime.class, new UtcLocalDateTime());
    }

    /** Zapisuje {@link LocalDateTime} jako czas UTC z jawnym {@code Z} na koncu. */
    private static final class UtcLocalDateTime extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator,
                              SerializerProvider serializers) throws IOException {
            /*
             * Zadnego przeliczania stref tu nie ma i byc nie moze: wartosc JEST juz w UTC (patrz
             * TimeZone.setDefault przy starcie).
             */
            generator.writeString(value.atOffset(ZoneOffset.UTC).format(UTC_FORMAT));
        }
    }
}
