package com.musicclubapp.service;

import com.musicclubapp.dto.ImportSummary;
import com.musicclubapp.dto.FavoritesResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testy ulubionych na prawdziwej bazie i z prawdziwym HTTP. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Ulubione - dodawanie, limity i import z Last.fm")
class FavoritesServiceTest {

    private static TestHttpServer server;

    static {
        try {
            server = new TestHttpServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry rejestr) {
        rejestr.add("app.music.deezer.base-url", () -> server.url());
        rejestr.add("app.lastfm.base-url", () -> server.url() + "/2.0/");
        rejestr.add("app.lastfm.api-key", () -> "klucz-testowy");
        rejestr.add("app.favorites.max-artists", () -> 3);
        rejestr.add("app.favorites.max-tracks", () -> 3);
    }

    @AfterAll
    static void closeServer() {
        server.close();
    }

    @Autowired
    private FavoritesService favoritesService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("ala", "ala@example.com", "hash"));

        server.odpowiadaj("/artist/27", """
            {"id": 27, "name": "Daft Punk", "picture_medium": "https://cdn/dp.jpg"}
            """);
        server.odpowiadaj("/artist/399", """
            {"id": 399, "name": "Radiohead", "picture_medium": "https://cdn/rh.jpg"}
            """);
        server.odpowiadaj("/artist/1", """
            {"id": 1, "name": "Ktos Inny", "picture_medium": null}
            """);
        server.odpowiadaj("/track/3135556", """
            {
              "id": 3135556,
              "title": "Harder, Better, Faster, Stronger",
              "artist": {"id": 27, "name": "Daft Punk"},
              "album": {"cover_medium": "https://cdn/discovery.jpg"}
            }
            """);
        server.odpowiadaj("/2.0/", """
            {"toptags": {"tag": [{"name": "electronic", "count": 100}]}}
            """);
    }

    @Test
    @DisplayName("dodany artysta ma nazwe i zdjecie POBRANE Z KATALOGU")
    void addingArtist() {
        FavoritesResponse po = favoritesService.addArtist("ala", "27");

        assertThat(po.artists()).singleElement().satisfies(a -> {
            assertThat(a.externalId()).isEqualTo("27");
            assertThat(a.name()).isEqualTo("Daft Punk");
            assertThat(a.imageUrl()).isEqualTo("https://cdn/dp.jpg");
        });
    }

    @Test
    @DisplayName("SERWER NIE UFA danym z zapytania - bierze tylko identyfikator")
    void serverDoesNotTrustNameFromRequest() {
        /*
         * Zapytanie da sie wyslac z pominieciem przegladarki, wiec nazwa i zdjecie moglyby byc
         * dowolne.
         */
        favoritesService.addArtist("ala", "27");

        assertThat(artistRepository.findByExternalId("27"))
            .isPresent()
            .get()
            .extracting(a -> a.getName())
            .isEqualTo("Daft Punk");
    }

    @Test
    @DisplayName("artysty spoza katalogu nie da sie dodac")
    void artistOutsideCatalog() {
        // Serwer nie ma /artist/999999 - to jest wymyslony wykonawca
        assertThatThrownBy(() -> favoritesService.addArtist("ala", "999999"))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(favoritesService.favorites("ala", "ala").artists()).isEmpty();
    }

    @Test
    @DisplayName("gatunki artysty zapisujemy przy pierwszym dodaniu")
    void genresAreStored() {
        favoritesService.addArtist("ala", "27");

        assertThat(artistRepository.findByExternalId("27").orElseThrow().getGenres())
            .containsExactly("electronic");
    }

    @Test
    @DisplayName("ten sam artysta u drugiej osoby NIE kosztuje zapytania do sieci")
    void artistRowIsSharedByEveryone() {
        userRepository.save(new User("bob", "bob@example.com", "hash"));

        favoritesService.addArtist("ala", "27");
        int poPierwszym = server.requests().size();

        favoritesService.addArtist("bob", "27");

        /* To nie jest optymalizacja dla samej optymalizacji. */
        assertThat(server.requests()).hasSize(poPierwszym);
        assertThat(favoritesService.favorites("bob", "bob").artists()).hasSize(1);
    }

    @Test
    @DisplayName("limit ulubionych zatrzymuje dodawanie")
    void favoritesLimit() {
        favoritesService.addArtist("ala", "27");
        favoritesService.addArtist("ala", "399");
        favoritesService.addArtist("ala", "1");

        // Limit ustawiony w @DynamicPropertySource na 3
        assertThatThrownBy(() -> favoritesService.addArtist("ala", "12345"))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("usuwamy POWIAZANIE, a nie artyste z katalogu")
    void removalKeepsArtistRow() {
        favoritesService.addArtist("ala", "27");
        favoritesService.removeArtist("ala", "27");

        assertThat(favoritesService.favorites("ala", "ala").artists()).isEmpty();
        /*
         * Gdyby usuwanie kasowalo wiersz z tabeli artists, zabraloby tego wykonawce takze
         * wszystkim innym, ktorzy nadal go maja u siebie.
         */
        assertThat(artistRepository.findByExternalId("27")).isPresent();
    }

    @Test
    @DisplayName("utwor niesie identyfikator wykonawcy - potrzebny do dopasowan")
    void addingTrack() {
        FavoritesResponse po = favoritesService.addTrack("ala", "3135556");

        assertThat(po.tracks()).singleElement().satisfies(u -> {
            assertThat(u.title()).isEqualTo("Harder, Better, Faster, Stronger");
            assertThat(u.artistName()).isEqualTo("Daft Punk");
            assertThat(u.artistExternalId()).isEqualTo("27");
        });
    }

    @Test
    @DisplayName("canEdit jest prawda TYLKO na wlasnym profilu")
    void canEditOnlyOnOwnProfile() {
        userRepository.save(new User("bob", "bob@example.com", "hash"));

        assertThat(favoritesService.favorites("ala", "ala").canEdit()).isTrue();
        assertThat(favoritesService.favorites("ala", "bob").canEdit()).isFalse();
    }

    @Test
    @DisplayName("import z Last.fm: nazwy stamtad, identyfikatory i zdjecia z Deezera")
    void importFromLastFmWorks() {
        /*
         * Tak wyglada podzial rol: Last.fm mowi, CZEGO ktos sluchal (same nazwy - ich API od 2019
         * roku nie oddaje uzytecznych zdjec), a Deezer mowi, KTO to jest.
         */
        server.odpowiadaj("/2.0/", """
            {"topartists": {"artist": [{"name": "Daft Punk"}, {"name": "Nie Ma Takiego"}]}}
            """);
        server.odpowiadaj("/search/artist", """
            {"data": [{"id": 27, "name": "Daft Punk", "picture_medium": "https://cdn/dp.jpg"}]}
            """);
        server.odpowiadaj("/search/track", """
            {"data": []}
            """);

        ImportSummary score = favoritesService.importFromLastFm("ala", "ktos");

        assertThat(score.addedArtists()).isEqualTo(1);
        assertThat(favoritesService.favorites("ala", "ala").artists())
            .singleElement()
            .extracting(a -> a.name())
            .isEqualTo("Daft Punk");
    }

    @Test
    @DisplayName("literowka w nazwie uzytkownika Last.fm konczy sie czytelnym bledem")
    void importWithUnknownUser() {
        server.odpowiadaj("/2.0/", """
            {"error": 6, "message": "User not found"}
            """);

        // Cicho zwrocona pusta lista wygladalaby jak "nic nie sluchasz"
        assertThatThrownBy(() -> favoritesService.importFromLastFm("ala", "literowka"))
            .isInstanceOf(OperationNotAllowedException.class);
    }
}
