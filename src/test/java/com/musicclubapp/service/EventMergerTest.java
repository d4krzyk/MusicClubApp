package com.musicclubapp.service;

import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventStatus;
import com.musicclubapp.entity.MusicEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiedy dwa wpisy z roznych zrodel to ten sam koncert - bez bazy, na samych regulach. */
@DisplayName("Laczenie wydarzen z roznych zrodel - zgodnosc")
class EventMergerTest {

    static final LocalDate DZIEN = LocalDate.of(2026, 11, 14);

    /** Nasze wydarzenie: Kult w Progresji, Warszawa, 20:00, bez wspolrzednych. */
    private static MusicEvent nasze() {
        MusicEvent e = WydarzeniaTestowe.wydarzenie("TM-1", "Kult", DZIEN, "Warszawa", "Rock", null, "Kult");
        e.place("V-1", "Progresja", "Warszawa", EventImportService.cityKey("Warszawa"), null, null, null);
        e.schedule(DZIEN, LocalTime.of(20, 0));
        return e;
    }

    private static ExternalEvent obce(String nazwa, LocalDate dzien, LocalTime godzina, String sala, String miasto,
                                      String kraj, Double lat, Double lon, String... sklad) {
        return new ExternalEvent(EventSource.BANDSINTOWN, "1", null, nazwa, EventStatus.SCHEDULED, dzien, godzina, sala,
            miasto, kraj, null, lat, lon, null, null, null, List.of(sklad));
    }

    @Test
    @DisplayName("ten sam dzien, sala ('Klub Progresja' = 'Progresja') i wykonawca - ten sam koncert")
    void sameConcert() {
        ExternalEvent x = obce("Kult + Hey", DZIEN, LocalTime.of(20, 30), "Klub Progresja", "Warszawa", "PL", null, null,
            "Kult", "Hey");
        assertThat(EventMerger.score(nasze(), x)).isEqualTo(3 + 3 + 1);
        // angielska nazwa miasta sprowadza sie do tego samego klucza
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, null, "Progresja", "Warsaw", "PL", null, null, "Kult")))
            .isPositive();
    }

    @Test
    @DisplayName("inny dzien, inny kraj, inne miasto przy tej samej nazwie sali - inny koncert")
    void differentDayCountryCity() {
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN.plusDays(1), null, "Progresja", "Warszawa", "PL", null, null, "Kult")))
            .isZero();
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, null, "Progresja", "Warszawa", "DE", null, null, "Kult")))
            .isZero();
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, null, "Progresja", "Kraków", "PL", null, null, "Kult")))
            .as("ta sama nazwa sali w innym miescie").isZero();
        // kraj nieznany po jednej stronie - nie przeszkadza
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, null, "Progresja", "Warszawa", null, null, null, "Kult")))
            .isPositive();
    }

    @Test
    @DisplayName("godziny: do 3 h roznicy to ten sam koncert (punkt za zgodnosc do 1 h), wiecej - popoludniowy i wieczorny")
    void times() {
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, LocalTime.of(23, 0), "Progresja", "Warszawa", "PL", null, null, "Kult")))
            .as("dokladnie 3 h: sala, wykonawca, nazwa").isEqualTo(3 + 3 + 2);
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, LocalTime.of(23, 1), "Progresja", "Warszawa", "PL", null, null, "Kult")))
            .isZero();
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, LocalTime.of(21, 0), "Progresja", "Warszawa", "PL", null, null, "Kult")))
            .as("dokladnie godzina - punkt za godzine").isEqualTo(6 + 2 + 1);
        assertThat(EventMerger.score(nasze(), obce("Kult", DZIEN, LocalTime.of(21, 1), "Progresja", "Warszawa", "PL", null, null, "Kult")))
            .isEqualTo(6 + 2);
    }

    @Test
    @DisplayName("sala po wspolrzednych (do 500 m), nawet przy innej nazwie; dalej i bez nazwy - nie")
    void coordinates() {
        MusicEvent e = nasze();
        e.place("V-1", "Progresja", "Warszawa", EventImportService.cityKey("Warszawa"), null, 52.2425, 20.9332);
        // ~300 m dalej, inna nazwa sali
        assertThat(EventMerger.score(e, obce("Inna nazwa", DZIEN, null, "Fort Wola Music Hall", "Warszawa", "PL", 52.2452, 20.9332, "Kult")))
            .isEqualTo(3 + 3);
        // ~2 km dalej - to juz inna sala
        assertThat(EventMerger.score(e, obce("Inna nazwa", DZIEN, null, "Stodoła", "Warszawa", "PL", 52.2605, 20.9332, "Kult")))
            .isZero();
        // inne miasto po kluczu, ale wspolrzedne w tej samej okolicy i ta sama sala po nazwie
        assertThat(EventMerger.score(e, obce("Kult", DZIEN, null, "Progresja", "Babice", "PL", 52.2500, 20.9000, "Kult")))
            .isPositive();
    }

    @Test
    @DisplayName("ta sama sala, ale inny wykonawca i inna nazwa - dwa koncerty tego dnia")
    void differentArtist() {
        assertThat(EventMerger.score(nasze(), obce("Hey", DZIEN, null, "Progresja", "Warszawa", "PL", null, null, "Hey")))
            .isZero();
        // wykonawca tylko w nazwie wydarzenia ("Kult - 40 lat") tez wystarczy
        assertThat(EventMerger.score(nasze(), obce("Trasa", DZIEN, null, "Progresja", "Warszawa", "PL", null, null, "kult")))
            .isPositive();
        MusicEvent bezSkladu = WydarzeniaTestowe.wydarzenie("TM-2", "Open'er Festival 2026", DZIEN, "Gdynia", "Rock", null);
        bezSkladu.place("V-2", "Lotnisko Kosakowo", "Gdynia", EventImportService.cityKey("Gdynia"), null, null, null);
        assertThat(EventMerger.score(bezSkladu, obce("Open'er Festival 2026", DZIEN, null, "Lotnisko Kosakowo", "Gdynia", "PL",
            null, null))).as("sama nazwa").isEqualTo(3 + 2);
        assertThat(EventMerger.score(bezSkladu, obce("Open'er", DZIEN, null, "Lotnisko Kosakowo", "Gdynia", "PL", null, null)))
            .as("nazwa zawarta w nazwie").isEqualTo(3 + 2);
        assertThat(EventMerger.score(bezSkladu, obce("Targi Winyli", DZIEN, null, "Lotnisko Kosakowo", "Gdynia", "PL", null, null)))
            .as("inna impreza w tym samym miejscu").isZero();
        assertThat(EventMerger.score(bezSkladu, obce("Opener", DZIEN, null, "Lotnisko Kosakowo", "Gdynia", "PL", null, null)))
            .as("pisownia sie rozni - bez wykonawcy nie zgadujemy").isZero();
    }

    @Test
    @DisplayName("nazwy sal: bez slow ogolnych, czesc wspolna co najmniej czteroliterowa")
    void venues() {
        assertThat(EventMerger.sameVenue("Klub Progresja", "Progresja")).isTrue();
        assertThat(EventMerger.sameVenue("COS Torwar", "Torwar")).isTrue();
        assertThat(EventMerger.sameVenue("Tauron Arena Kraków", "Tauron Arena")).isTrue();
        assertThat(EventMerger.sameVenue("Stodoła", "Progresja")).isFalse();
        assertThat(EventMerger.sameVenue("Klub", "Klub Hydrozagadka")).as("samo ogolne slowo").isFalse();
        assertThat(EventMerger.sameVenue("B90", "B90 Gdańsk")).as("za krotka wspolna nazwa").isFalse();
        assertThat(EventMerger.sameVenue(null, "Progresja")).isFalse();
    }

    @Test
    @DisplayName("najlepsze dopasowanie wygrywa; bez dopasowania - nic")
    void best() {
        MusicEvent slabsze = nasze();
        MusicEvent lepsze = WydarzeniaTestowe.wydarzenie("TM-3", "Kult", DZIEN, "Warszawa", "Rock", null, "Kult");
        lepsze.place("V-1", "Progresja", "Warszawa", EventImportService.cityKey("Warszawa"), null, 52.2425, 20.9332);
        lepsze.schedule(DZIEN, LocalTime.of(20, 0));
        ExternalEvent x = obce("Kult", DZIEN, LocalTime.of(20, 0), "Progresja", "Warszawa", "PL", 52.2426, 20.9333, "Kult");
        assertThat(EventMerger.best(List.of(slabsze, lepsze), x)).contains(lepsze);
        assertThat(EventMerger.best(List.of(slabsze), obce("Hey", DZIEN, null, "Stodoła", "Warszawa", "PL", null, null, "Hey")))
            .isEmpty();
    }
}
