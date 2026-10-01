package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Poziom bliskosci")
class LocationScoreTest {

    @Test
    @DisplayName("progi: miasto, aglomeracja, okolica, region, daleko")
    void levels() {
        assertThat(LocationScore.level(null)).isZero();
        assertThat(LocationScore.level(0.0)).isEqualTo(5);
        assertThat(LocationScore.level(0.99)).isEqualTo(5);
        assertThat(LocationScore.level(1.0)).isEqualTo(4);
        assertThat(LocationScore.level(30.0)).isEqualTo(4);
        assertThat(LocationScore.level(30.1)).isEqualTo(3);
        assertThat(LocationScore.level(60.0)).isEqualTo(3);
        assertThat(LocationScore.level(60.1)).isEqualTo(2);
        assertThat(LocationScore.level(120.0)).isEqualTo(2);
        assertThat(LocationScore.level(120.1)).isEqualTo(1);
        assertThat(LocationScore.level(250.0)).isEqualTo(1);
        assertThat(LocationScore.level(250.1)).isZero();
        assertThat(LocationScore.level(900.0)).isZero();
    }

    @Test
    @DisplayName("podpis bez odleglosci: to samo miasto, w okolicy albo nic")
    void closeness() {
        assertThat(LocationScore.Closeness.of(null)).isNull();
        assertThat(LocationScore.Closeness.of(0.0)).isEqualTo(LocationScore.Closeness.SAME_CITY);
        assertThat(LocationScore.Closeness.of(0.5)).isEqualTo(LocationScore.Closeness.SAME_CITY);
        assertThat(LocationScore.Closeness.of(45.0)).isEqualTo(LocationScore.Closeness.NEARBY);
        assertThat(LocationScore.Closeness.of(60.0)).isEqualTo(LocationScore.Closeness.NEARBY);
        assertThat(LocationScore.Closeness.of(60.1)).isNull();
    }
}
