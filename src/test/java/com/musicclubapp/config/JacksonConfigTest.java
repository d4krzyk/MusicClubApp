package com.musicclubapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Pilnuje, ze daty wychodza z API z jawna strefa czasowa. */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Daty w API maja jawna strefe (UTC)")
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("LocalDateTime zapisuje sie z koncowka Z")
    void datesCarryExplicitZone() throws Exception {
        String json = objectMapper.writeValueAsString(
            LocalDateTime.of(2026, 8, 28, 12, 0, 0));

        assertThat(json)
            .describedAs("bez koncowki Z przegladarka odczyta te godzine jako czas "
                + "lokalny i przesunie ja o roznice stref")
            .isEqualTo("\"2026-08-28T12:00:00Z\"");
    }

    @Test
    @DisplayName("godzina NIE jest przy okazji przeliczana")
    void theHourItselfIsUnchanged() throws Exception {
        /*
         * Dopisanie "Z" ma tylko NAZWAC strefe, ktora i tak juz obowiazuje (zegar serwera jest
         * przypiety do UTC przy starcie aplikacji).
         */
        String json = objectMapper.writeValueAsString(
            LocalDateTime.of(2026, 1, 5, 23, 45, 30));

        assertThat(json).isEqualTo("\"2026-01-05T23:45:30Z\"");
    }

    @Test
    @DisplayName("data w obiekcie tez dostaje strefe")
    void nestedDatesAreCoveredToo() throws Exception {
        record Wiadomosc(String tresc, LocalDateTime createdAt) { }

        String json = objectMapper.writeValueAsString(
            new Wiadomosc("czesc", LocalDateTime.of(2026, 8, 28, 9, 5, 1)));

        assertThat(json).contains("\"createdAt\":\"2026-08-28T09:05:01Z\"");
    }
}
