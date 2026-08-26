package com.musicclubapp.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testy czytania odpowiedzi Last.fm.
 *
 * <p>Odpowiedzi sa przepisane z dokumentacji Last.fm i skrocone. Najwiecej
 * uwagi poswiecamy dwóm rzeczom, ktore w tym API potrafia zaskoczyc:
 * <b>bledy przychodza z kodem HTTP 200</b> (a wiec status nic nie mowi)
 * oraz <b>tagi to nie sa gatunki</b> - wsrod najpopularniejszych sa
 * "seen live" i "favorites".</p>
 */
@DisplayName("Last.fm - czytanie historii sluchania")
class LastFmServiceTest {

    private static final String TOP_ARTYSCI = """
        {
          "topartists": {
            "artist": [
              { "name": "Radiohead", "playcount": "1420", "mbid": "a74b1b7f" },
              { "name": "Boards of Canada", "playcount": "980" },
              { "name": "Aphex Twin", "playcount": "775" }
            ],
            "@attr": { "user": "ktos", "total": "3" }
          }
        }
        """;

    private static final String TOP_UTWORY = """
        {
          "toptracks": {
            "track": [
              {
                "name": "Weird Fishes/ Arpeggi",
                "playcount": "88",
                "artist": { "name": "Radiohead", "mbid": "a74b1b7f" }
              },
              {
                "name": "Roygbiv",
                "playcount": "64",
                "artist": { "name": "Boards of Canada" }
              }
            ]
          }
        }
        """;

    private static final String TAGI = """
        {
          "toptags": {
            "tag": [
              { "name": "alternative", "count": 100 },
              { "name": "seen live",   "count": 96  },
              { "name": "rock",        "count": 84  },
              { "name": "favorites",   "count": 61  },
              { "name": "indie",       "count": 55  },
              { "name": "cos-czego-nikt-nie-uzywa", "count": 3 }
            ]
          }
        }
        """;

    private TestHttpServer server;
    private LastFmService lastFm;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        lastFm = new LastFmService("klucz-testowy", server.url() + "/2.0/", 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("bez klucza API import jest wylaczony - i nie jest to blad")
    void withoutApiKey() {
        LastFmService withoutApiKey = new LastFmService("", server.url() + "/2.0/", 2000);

        assertThat(withoutApiKey.available()).isFalse();
        // Frontend chowa wtedy przycisk - lepsze to niz przycisk, ktory
        // zawsze konczy sie bledem
        assertThat(withoutApiKey.artistGenres("Radiohead")).isEmpty();
    }

    @Test
    @DisplayName("z kluczem import jest dostepny")
    void withApiKey() {
        assertThat(lastFm.available()).isTrue();
    }

    @Test
    @DisplayName("najczesciej sluchani artysci - w kolejnosci od Last.fm")
    void topArtists() {
        server.odpowiadaj("/2.0/", TOP_ARTYSCI);

        List<String> names = lastFm.topArtists("ktos", 15);

        // Kolejnosc ma znaczenie: pierwszy jest najczesciej sluchany
        assertThat(names).containsExactly("Radiohead", "Boards of Canada", "Aphex Twin");
    }

    @Test
    @DisplayName("najczesciej sluchane utwory niosa nazwe wykonawcy")
    void topTracks() {
        server.odpowiadaj("/2.0/", TOP_UTWORY);

        List<LastFmService.ListenedTrack> tracks = lastFm.topTracks("ktos", 15);

        assertThat(tracks).hasSize(2);
        assertThat(tracks.get(0).title()).isEqualTo("Weird Fishes/ Arpeggi");
        // Bez wykonawcy nie dalo by sie potem odnalezc tego utworu w Deezerze
        assertThat(tracks.get(0).artistName()).isEqualTo("Radiohead");
    }

    @Test
    @DisplayName("z tagow zostaja GATUNKI - 'seen live' i 'favorites' wypadaja")
    void tagsAreFiltered() {
        server.odpowiadaj("/2.0/", TAGI);

        var genres = lastFm.artistGenres("Radiohead");

        assertThat(genres).containsExactlyInAnyOrder("alternative", "rock", "indie");
        /*
         * To jest sedno: bez odsiania tych etykiet dopasowanie po gatunkach
         * laczyloby ludzi na zasadzie "oboje byli na jakims koncercie".
         */
        assertThat(genres).doesNotContain("seen live", "favorites");
        // Tag z popularnoscia 3 to zwykle etykieta wpisana przez jedna osobe
        assertThat(genres).doesNotContain("cos-czego-nikt-nie-uzywa");
    }

    @Test
    @DisplayName("nieznany uzytkownik konczy sie WYJATKIEM, a nie pusta lista")
    void unknownLastFmUser() {
        /*
         * Ta roznica jest istotna. Literowka w nazwie to pomylka, o ktorej
         * uzytkownik musi sie dowiedziec - cicho zwrocona pusta lista
         * wygladalaby jak "nic nie sluchasz".
         *
         * Uwaga: Last.fm oddaje ten blad z kodem HTTP 200 i polem "error"
         * w tresci. Kod patrzacy tylko na status nie zauwazylby niczego.
         */
        server.odpowiadaj("/2.0/", """
            {"error": 6, "message": "User not found"}
            """);

        assertThatThrownBy(() -> lastFm.topArtists("nieistniejacy", 15))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("awaria Last.fm to inny przypadek niz zla nazwa uzytkownika")
    void serviceOutage() {
        server.close();

        // Osobny typ wyjatku, bo i komunikat dla uzytkownika ma byc inny:
        // "sprawdz pisownie" kontra "sprobuj za chwile"
        assertThatThrownBy(() -> lastFm.topArtists("ktos", 15))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("klucz i nazwa uzytkownika trafiaja do adresu")
    void requestParameters() {
        server.odpowiadaj("/2.0/", TOP_ARTYSCI);

        lastFm.topArtists("moj login", 15);

        assertThat(server.requests()).singleElement().asString()
            .contains("method=user.gettopartists")
            .contains("user=moj login")
            .contains("api_key=klucz-testowy")
            .contains("format=json");
    }

    @Test
    @DisplayName("blad przy gatunkach NIE przerywa niczego - artysta bez gatunkow jest OK")
    void genresDuringOutage() {
        server.close();

        // Inaczej niz przy historii sluchania: tu brak danych to normalny
        // stan, a nie cos, o czym trzeba komukolwiek mowic
        assertThat(lastFm.artistGenres("Radiohead")).isEmpty();
    }
}
