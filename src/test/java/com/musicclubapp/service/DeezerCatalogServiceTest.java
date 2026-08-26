package com.musicclubapp.service;

import com.musicclubapp.dto.CatalogArtist;
import com.musicclubapp.dto.CatalogTrack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy czytania odpowiedzi Deezera.
 *
 * <p>Odpowiedzi ponizej sa przepisane z dokumentacji Deezera i skrocone
 * do pol, ktorych uzywamy. Sprawdzamy trzy rzeczy, ktore latwo zepsuc
 * i trudno zauwazyc: <b>czy dobrze skladamy adres</b> (kodowanie spacji),
 * <b>czy czytamy wlasciwe pola</b> i <b>czy awaria nie wywraca strony</b>.</p>
 */
@DisplayName("Katalog Deezera - czytanie odpowiedzi")
class DeezerCatalogServiceTest {

    private static final String WYNIK_ARTYSTOW = """
        {
          "data": [
            {
              "id": 27,
              "name": "Daft Punk",
              "picture_medium": "https://cdn.deezer.com/daft-punk-250.jpg",
              "nb_fan": 5300000,
              "type": "artist"
            },
            {
              "id": 4645540,
              "name": "Daft Punk Tribute",
              "picture_medium": "https://cdn.deezer.com/tribute-250.jpg",
              "type": "artist"
            }
          ],
          "total": 2
        }
        """;

    private static final String WYNIK_UTWOROW = """
        {
          "data": [
            {
              "id": 3135556,
              "title": "Harder, Better, Faster, Stronger",
              "duration": 224,
              "artist": { "id": 27, "name": "Daft Punk" },
              "album": {
                "id": 302127,
                "title": "Discovery",
                "cover_medium": "https://cdn.deezer.com/discovery-250.jpg"
              }
            }
          ]
        }
        """;

    private static final String JEDEN_ARTYSTA = """
        {
          "id": 27,
          "name": "Daft Punk",
          "picture_medium": "https://cdn.deezer.com/daft-punk-250.jpg",
          "nb_album": 24
        }
        """;

    private TestHttpServer server;
    private DeezerCatalogService catalog;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        catalog = new DeezerCatalogService(server.url(), 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("wyszukiwanie artystow czyta id, nazwe i zdjecie")
    void artistSearch() {
        server.odpowiadaj("/search/artist", WYNIK_ARTYSTOW);

        List<CatalogArtist> score = catalog.searchArtists("daft punk", 8);

        assertThat(score).hasSize(2);
        assertThat(score.get(0).externalId()).isEqualTo("27");
        assertThat(score.get(0).name()).isEqualTo("Daft Punk");
        assertThat(score.get(0).imageUrl()).endsWith("daft-punk-250.jpg");
    }

    @Test
    @DisplayName("nazwa ze spacja jest poprawnie zakodowana w adresie")
    void urlEncoding() {
        server.odpowiadaj("/search/artist", WYNIK_ARTYSTOW);

        catalog.searchArtists("daft punk", 8);

        /*
         * Bez kodowania spacja rozbilaby adres i zapytanie w ogole by nie
         * doszlo. Sprawdzamy tu, ze parametry naprawde dotarly - serwer
         * zapisuje adres po rozkodowaniu, wiec widzimy to, co odczytal Deezer.
         */
        assertThat(server.requests()).singleElement().asString()
            .contains("q=daft punk")
            .contains("limit=8");
    }

    @Test
    @DisplayName("limit jest przycinany - jedno zapytanie nie sciagnie setek pozycji")
    void limitIsClamped() {
        server.odpowiadaj("/search/artist", WYNIK_ARTYSTOW);

        catalog.searchArtists("cokolwiek", 5000);

        assertThat(server.requests()).singleElement().asString().contains("limit=24");
    }

    @Test
    @DisplayName("utwor niesie ze soba WYKONAWCE - to on liczy sie w dopasowaniu")
    void trackSearch() {
        server.odpowiadaj("/search/track", WYNIK_UTWOROW);

        CatalogTrack track = catalog.searchTracks("harder better", 8).get(0);

        assertThat(track.externalId()).isEqualTo("3135556");
        assertThat(track.title()).isEqualTo("Harder, Better, Faster, Stronger");
        assertThat(track.artistName()).isEqualTo("Daft Punk");
        // Identyfikator wykonawcy pozwala liczyc wspolnych artystow bez
        // porownywania tekstow
        assertThat(track.artistExternalId()).isEqualTo("27");
        assertThat(track.imageUrl()).endsWith("discovery-250.jpg");
    }

    @Test
    @DisplayName("pobranie po identyfikatorze - to na tym stoi ochrona przed wymyslonymi artystami")
    void fetchArtistById() {
        server.odpowiadaj("/artist/27", JEDEN_ARTYSTA);

        Optional<CatalogArtist> score = catalog.fetchArtist("27");

        assertThat(score).isPresent();
        assertThat(score.get().name()).isEqualTo("Daft Punk");
    }

    @Test
    @DisplayName("nieistniejacy identyfikator daje pusty wynik, a nie wyjatek")
    void unknownIdentifier() {
        // Serwer nie ma /artist/999 - odpowie 404
        assertThat(catalog.fetchArtist("999")).isEmpty();
    }

    @Test
    @DisplayName("identyfikator, ktory nie jest liczba, w ogole nie idzie do sieci")
    void identifierIsNotANumber() {
        /*
         * To pierwsza linia obrony: identyfikatory Deezera sa liczbami, wiec
         * cokolwiek innego odrzucamy, zanim doklejymy to do adresu zapytania.
         */
        assertThat(catalog.fetchArtist("../../cos")).isEmpty();
        assertThat(catalog.fetchArtist("abc")).isEmpty();
        assertThat(catalog.fetchArtist(null)).isEmpty();

        assertThat(server.requests()).isEmpty();
    }

    @Test
    @DisplayName("blad Deezera przychodzi z kodem 200 - i mimo to go rozpoznajemy")
    void errorInsideResponseBody() {
        /*
         * Pulapka warta testu: Deezer sygnalizuje bledy POLEM "error"
         * w tresci, a status HTTP zostaje 200. Kod patrzacy tylko na status
         * uznalby przekroczony limit zapytan za "brak wynikow".
         */
        server.odpowiadaj("/search/artist", """
            {"error": {"type": "Exception", "message": "Quota limit exceeded", "code": 4}}
            """);

        assertThat(catalog.searchArtists("cokolwiek", 8)).isEmpty();
    }

    @Test
    @DisplayName("gdy Deezer nie odpowiada, dostajemy pusta liste zamiast bledu")
    void serviceUnavailable() {
        server.close();   // symulujemy awarie

        // Uzytkownik zobaczy "brak wynikow", a reszta profilu bedzie dzialac
        assertThat(catalog.searchArtists("daft punk", 8)).isEmpty();
        assertThat(catalog.searchTracks("cokolwiek", 8)).isEmpty();
    }

    @Test
    @DisplayName("pusta fraza nie generuje zadnego zapytania")
    void emptyPhrase() {
        assertThat(catalog.searchArtists("", 8)).isEmpty();
        assertThat(catalog.searchArtists("   ", 8)).isEmpty();
        assertThat(catalog.searchArtists(null, 8)).isEmpty();

        assertThat(server.requests()).isEmpty();
    }
}
