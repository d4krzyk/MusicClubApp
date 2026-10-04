package com.musicclubapp.service;

import com.musicclubapp.entity.LookingFor;
import com.musicclubapp.entity.LookingForConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Poznawaj - punkty gustu, pasma odleglosci, zapis 'szukam'")
class DiscoverMatchTest {

    @Test
    @DisplayName("punkty: wykonawca 5, utwor 3, gatunek 1; poziomy na progach 1 / 5 / 12 / 25 (przyklady z opisu klasy)")
    void levels() {
        assertThat(DiscoverMatch.score(1, 0, 2)).isEqualTo(7);
        assertThat(DiscoverMatch.level(DiscoverMatch.score(1, 0, 2))).isEqualTo(2);
        assertThat(DiscoverMatch.level(DiscoverMatch.score(3, 1, 5))).isEqualTo(3);
        assertThat(DiscoverMatch.level(DiscoverMatch.score(0, 0, 2))).isEqualTo(1);
        assertThat(DiscoverMatch.level(0)).isZero();
        assertThat(DiscoverMatch.level(1)).isEqualTo(1);
        assertThat(DiscoverMatch.level(4)).isEqualTo(1);
        assertThat(DiscoverMatch.level(5)).isEqualTo(2);
        assertThat(DiscoverMatch.level(11)).isEqualTo(2);
        assertThat(DiscoverMatch.level(12)).isEqualTo(3);
        assertThat(DiscoverMatch.level(24)).isEqualTo(3);
        assertThat(DiscoverMatch.level(25)).isEqualTo(4);
        assertThat(DiscoverMatch.level(500)).isEqualTo(4);
    }

    @Test
    @DisplayName("pasma odleglosci z tej samej skali co reszta aplikacji; nie wiadomo = null")
    void proximity() {
        assertThat(DiscoverService.proximity(null)).isNull();
        assertThat(DiscoverService.proximity(0.0)).isEqualTo("SAME_CITY");
        assertThat(DiscoverService.proximity(12.0)).isEqualTo("KM_30");
        assertThat(DiscoverService.proximity(30.0)).isEqualTo("KM_30");
        assertThat(DiscoverService.proximity(45.0)).isEqualTo("KM_60");
        assertThat(DiscoverService.proximity(100.0)).isEqualTo("KM_120");
        assertThat(DiscoverService.proximity(250.0)).isEqualTo("KM_250");
        assertThat(DiscoverService.proximity(280.0)).isEqualTo("FAR");
    }

    @Test
    @DisplayName("'szukam' w jednej kolumnie: kolejnosc stala, pusto = NULL, nieznana nazwa pominieta")
    void lookingForColumn() {
        LookingForConverter c = new LookingForConverter();
        assertThat(c.convertToDatabaseColumn(Set.of(LookingFor.PLAYLISTS, LookingFor.CONCERT_BUDDIES)))
            .isEqualTo("CONCERT_BUDDIES,PLAYLISTS");
        assertThat(c.convertToDatabaseColumn(Set.of())).isNull();
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("JAMMING, KOSMOS ,PARTIES"))
            .isEqualTo(EnumSet.of(LookingFor.JAMMING, LookingFor.PARTIES));
        assertThat(c.convertToEntityAttribute(null)).isEmpty();
        assertThat(c.convertToEntityAttribute(" ")).isEmpty();
    }
}
