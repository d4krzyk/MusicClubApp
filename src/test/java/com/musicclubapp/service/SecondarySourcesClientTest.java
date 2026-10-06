package com.musicclubapp.service;

import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bandsintown i Songkick na udawanym serwerze - z tego srodowiska prawdziwe sa niedostepne, a klucze wymagaja zgody. */
@DisplayName("Zrodla poboczne: Bandsintown i Songkick")
class SecondarySourcesClientTest {

    static final String BIT = """
        [
          { "id": "1001", "url": "https://www.bandsintown.com/e/1001?app_id=x",
            "datetime": "2026-11-14T20:00:00", "title": "",
            "description": "Trasa jesienna",
            "venue": { "name": "Klub Progresja", "latitude": "52.2425", "longitude": "20.9332",
                       "city": "Warszawa", "country": "Poland", "street_address": "Fort Wola 22" },
            "offers": [ { "type": "Tickets", "url": "javascript:alert(1)" },
                        { "type": "Tickets", "url": "https://bilety.example/kult" } ],
            "lineup": [ "Kult", "Hey" ] },
          { "id": "1002", "datetime": "2026-11-20T00:00:00", "title": "Kult w Berlinie",
            "venue": { "name": "SO36", "city": "Berlin", "country": "Germany" }, "lineup": [] },
          { "id": "1003", "datetime": "nie-data" }
        ]
        """;

    static final String SK = """
        { "resultsPage": { "status": "ok", "page": 1, "perPage": 50, "totalEntries": 2, "results": { "event": [
          { "id": 501, "type": "Concert", "status": "cancelled", "displayName": "Kult at Torwar (November 14, 2026)",
            "uri": "https://www.songkick.com/concerts/501-kult",
            "start": { "date": "2026-11-14", "time": "19:30:00" },
            "performance": [ { "displayName": "Kult", "artist": { "displayName": "Kult" } },
                             { "displayName": "Hey", "artist": { "displayName": "Hey" } } ],
            "venue": { "displayName": "Torwar", "lat": 52.2135, "lng": 21.0475,
                       "metroArea": { "displayName": "Warsaw", "country": { "displayName": "Poland" } } } },
          { "id": 502, "type": "Festival", "status": "ok", "displayName": "Open'er Festival 2026 (July 1, 2026)",
            "series": { "displayName": "Open'er Festival" },
            "start": { "date": "2026-07-01" }, "performance": [],
            "venue": { "displayName": "Lotnisko Kosakowo", "lat": null, "lng": null,
                       "metroArea": { "displayName": "Gdynia", "country": { "displayName": "Poland" } } } }
        ] } } }
        """;

    private TestHttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("Bandsintown: wydarzenia wykonawcy, kraj z nazwy, bezpieczne bilety, sklad zamiast pustego tytulu")
    void bandsintown() {
        server.odpowiadaj("/artists/Kult/events", BIT);
        BandsintownClient bit = new BandsintownClient("tajny-app-id", server.url(), 2000);
        List<ExternalEvent> lista = bit.artistEvents("Kult");
        assertThat(lista).hasSize(2);
        ExternalEvent w = lista.get(0);
        assertThat(w.source()).isEqualTo(EventSource.BANDSINTOWN);
        assertThat(w.externalId()).isEqualTo("1001");
        assertThat(w.date()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(w.time()).isEqualTo(LocalTime.of(20, 0));
        assertThat(w.countryCode()).isEqualTo("PL");
        assertThat(w.name()).as("pusty tytul = sklad").isEqualTo("Kult + Hey");
        assertThat(w.performers()).containsExactly("Kult", "Hey");
        assertThat(w.latitude()).isEqualTo(52.2425);
        assertThat(w.ticketUrl()).as("javascript: pominiety").isEqualTo("https://bilety.example/kult");
        assertThat(w.address()).isEqualTo("Fort Wola 22");
        assertThat(w.description()).isEqualTo("Trasa jesienna");
        ExternalEvent berlin = lista.get(1);
        assertThat(berlin.countryCode()).isEqualTo("DE");
        assertThat(berlin.time()).as("polnoc = godzina nieznana").isNull();
        assertThat(berlin.performers()).as("pusty sklad = pytany wykonawca").containsExactly("Kult");
        assertThat(berlin.latitude()).isNull();
        assertThat(server.requests().get(0)).contains("app_id=tajny-app-id").contains("date=upcoming");
    }

    @Test
    @DisplayName("Bandsintown: ukosnik w nazwie zakodowany podwojnie, nieznany wykonawca = pusto, awaria bez klucza w opisie")
    void bandsintownEdges() {
        BandsintownClient bit = new BandsintownClient("tajny-app-id", server.url(), 2000);
        assertThat(bit.artistEvents("AC/DC")).as("404 = nieznany").isEmpty();
        assertThat(server.requests().get(0)).contains("/artists/AC%2FDC/events");
        server.odpowiadaj("/artists/Nikt/events", "{\"errorMessage\": \"[NotFound] The artist was not found\"}");
        assertThat(bit.artistEvents("Nikt")).isEmpty();
        server.odpowiadaj("/artists/Awaria/events", q -> new TestHttpServer.Odpowiedz(500, "{}"));
        assertThatThrownBy(() -> bit.artistEvents("Awaria")).hasMessageContaining("HTTP 500")
            .hasMessageNotContaining("tajny-app-id");
        assertThat(new BandsintownClient(" ", server.url(), 2000).available()).isFalse();
        assertThat(BandsintownClient.nazwaWAdresie("Sigur Rós?")).isEqualTo("Sigur%20R%C3%B3s%253F");
    }

    @Test
    @DisplayName("Songkick: okolica punktu, odwolane, festiwal po nazwie serii, ostatnia strona, blad bez klucza")
    void songkick() {
        server.odpowiadaj("/api/3.0/events.json", SK);
        SongkickClient sk = new SongkickClient("klucz-sk", server.url() + "/api/3.0", 2000);
        SongkickClient.Page strona = sk.eventsNear(52.2297, 21.0122, LocalDate.of(2026, 10, 6), LocalDate.of(2027, 10, 6), 1);
        assertThat(strona.last()).isTrue();
        assertThat(strona.events()).hasSize(2);
        ExternalEvent kult = strona.events().get(0);
        assertThat(kult.source()).isEqualTo(EventSource.SONGKICK);
        assertThat(kult.externalId()).isEqualTo("501");
        assertThat(kult.name()).isEqualTo("Kult, Hey");
        assertThat(kult.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(kult.time()).isEqualTo(LocalTime.of(19, 30));
        assertThat(kult.city()).isEqualTo("Warsaw");
        assertThat(kult.countryCode()).isEqualTo("PL");
        assertThat(kult.url()).isEqualTo("https://www.songkick.com/concerts/501-kult");
        ExternalEvent festiwal = strona.events().get(1);
        assertThat(festiwal.name()).isEqualTo("Open'er Festival");
        assertThat(festiwal.time()).isNull();
        assertThat(festiwal.latitude()).isNull();
        assertThat(server.requests().get(0)).contains("location=geo:52.2297,21.0122").contains("min_date=2026-10-06")
            .contains("per_page=50");

        server.odpowiadaj("/api/3.0/events.json", "{\"resultsPage\":{\"status\":\"error\",\"error\":{\"message\":\"Invalid apikey klucz-sk\"}}}");
        assertThatThrownBy(() -> sk.eventsNear(52.2, 21.0, LocalDate.of(2026, 10, 6), LocalDate.of(2027, 10, 6), 1))
            .hasMessageContaining("Invalid apikey").hasMessageNotContaining("klucz-sk");
        assertThat(SongkickClient.bezDaty("Kult at Torwar (November 14, 2026)")).isEqualTo("Kult at Torwar");
    }

    @Test
    @DisplayName("kraj z angielskiej nazwy")
    void countries() {
        assertThat(Countries.isoFromEnglishName("Poland")).isEqualTo("PL");
        assertThat(Countries.isoFromEnglishName("United States")).isEqualTo("US");
        assertThat(Countries.isoFromEnglishName("Czech Republic")).isEqualTo("CZ");
        assertThat(Countries.isoFromEnglishName("Czechia")).isEqualTo("CZ");
        assertThat(Countries.isoFromEnglishName("de")).isEqualTo("DE");
        assertThat(Countries.isoFromEnglishName("Atlantyda")).isNull();
        assertThat(Countries.isoFromEnglishName(null)).isNull();
    }
}
