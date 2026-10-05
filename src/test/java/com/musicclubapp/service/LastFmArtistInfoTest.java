package com.musicclubapp.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opis wykonawcy z Last.fm ({@code artist.getInfo}) na udawanym serwerze - ksztalt odpowiedzi wg dokumentacji Last.fm
 * (z tego srodowiska serwis jest zablokowany).
 */
@DisplayName("Last.fm - opis wykonawcy")
class LastFmArtistInfoTest {

    static final String OPIS = """
        {
          "artist": {
            "name": "Kult",
            "url": "https://www.last.fm/music/Kult",
            "stats": { "listeners": "312877", "playcount": "9876543" },
            "similar": { "artist": [ { "name": "T.Love" }, { "name": "Dezerter" }, { "name": "Hey" },
                                     { "name": "Lady Pank" }, { "name": "Perfect" }, { "name": "Republika" } ] },
            "bio": {
              "summary": "Kult &ndash; polski zesp&oacute;&#322; rockowy zalo&#380;ony w 1982 w <b>Warszawie</b>.\\n\\n \\"Arahja\\" &amp; inne. <a href=\\"https://www.last.fm/music/Kult\\">Czytaj wi&#281;cej na Last.fm</a>",
              "content": "dlugi tekst"
            }
          }
        }
        """;

    private TestHttpServer server;
    private LastFmService lastFm;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        lastFm = new LastFmService("klucz-lastfm", server.url() + "/2.0/", 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("opis: zwykly tekst bez odnosnika 'Czytaj wiecej', sluchacze, najwyzej 5 podobnych, jezyk w zapytaniu")
    void info() {
        server.odpowiadaj("/2.0/", OPIS);
        var info = lastFm.artistInfo("kult", "pl").orElseThrow();
        assertThat(info.name()).isEqualTo("Kult");
        assertThat(info.bio()).startsWith("Kult").contains("Warszawie").contains("\"Arahja\" & inne.")
            .doesNotContain("<", "Czytaj", "&amp;");
        assertThat(info.url()).isEqualTo("https://www.last.fm/music/Kult");
        assertThat(info.listeners()).isEqualTo(312877L);
        assertThat(info.similar()).containsExactly("T.Love", "Dezerter", "Hey", "Lady Pank", "Perfect");
        assertThat(server.requests().get(0)).contains("method=artist.getinfo").contains("artist=kult")
            .contains("lang=pl").contains("autocorrect=1");

        lastFm.artistInfo("kult", "en");
        assertThat(server.requests().get(1)).doesNotContain("lang=");
    }

    @Test
    @DisplayName("nie ma takiego wykonawcy = odpowiedz bez opisu; awaria, inny blad i obcy ksztalt = brak odpowiedzi")
    void errors() {
        server.odpowiadaj("/2.0/", "{\"error\": 6, \"message\": \"The artist you supplied could not be found\"}");
        var brak = lastFm.artistInfo("Nikt Taki", "pl").orElseThrow();
        assertThat(brak.bio()).isNull();
        assertThat(brak.name()).isEqualTo("Nikt Taki");

        server.odpowiadaj("/2.0/", "{\"error\": 29, \"message\": \"Rate limit exceeded\"}");
        assertThat(lastFm.artistInfo("Kult", "pl")).isEmpty();
        server.odpowiadaj("/2.0/", "{\"cos\": {}}");
        assertThat(lastFm.artistInfo("Kult", "pl")).isEmpty();
        server.odpowiadaj("/2.0/", "nie json");
        assertThat(lastFm.artistInfo("Kult", "pl")).isEmpty();
        assertThat(new LastFmService("", server.url() + "/2.0/", 2000).artistInfo("Kult", "pl")).isEmpty();
    }

    @Test
    @DisplayName("adres spoza last.fm odrzucony; pusty opis = null; za dlugi uciety na granicy slowa")
    void bioAndUrl() {
        server.odpowiadaj("/2.0/", """
            { "artist": { "name": "X", "url": "https://zly.example/x", "bio": { "summary": " <a href=\\"https://www.last.fm/music/X\\">Read more on Last.fm</a>" } } }
            """);
        var x = lastFm.artistInfo("X", "en").orElseThrow();
        assertThat(x.url()).isNull();
        assertThat(x.bio()).isNull();
        assertThat(x.listeners()).isNull();

        String dlugi = LastFmService.bioText("slowo ".repeat(400));
        assertThat(dlugi).endsWith("slowo…");
        assertThat(dlugi.length()).isLessThanOrEqualTo(LastFmService.MAX_BIO + 1);
        assertThat(LastFmService.bioText("a &lt;b&gt; &amp;nbsp; c")).isEqualTo("a <b> &nbsp; c");
        // znaczniki w zdaniu znikaja bez sladu ("w <b>Warszawie</b>." - bez spacji przed kropka), akapity dziela slowa
        assertThat(LastFmService.bioText("w <b>Warszawie</b>. Zesp<i>o</i>l")).isEqualTo("w Warszawie. Zespol");
        assertThat(LastFmService.bioText("koniec<br>poczatek</p><p>dalej<br/>i<li>punkt"))
            .isEqualTo("koniec poczatek dalej i punkt");
    }
}
