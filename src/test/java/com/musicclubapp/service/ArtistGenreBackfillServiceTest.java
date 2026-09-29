package com.musicclubapp.service;

import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Uzupelnianie gatunkow ulubionych artystow, dodanych bez klucza Last.fm. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Gatunki ulubionych artystow - uzupelnianie w tle")
class ArtistGenreBackfillServiceTest {

    @Autowired private ArtistRepository artists;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private TestHttpServer lastFmServer;
    private ArtistGenreBackfillService backfill;
    private User ala;

    @BeforeEach
    void setUp() throws IOException {
        lastFmServer = new TestHttpServer();
        lastFmServer.odpowiadaj("/2.0/", query -> {
            if (query.contains("artist=Radiohead")) {
                return TestHttpServer.Odpowiedz.ok(
                    "{\"toptags\":{\"tag\":[{\"name\":\"alternative\",\"count\":100},{\"name\":\"rock\",\"count\":80}]}}");
            }
            if (query.contains("artist=Nikomu Nieznany")) {
                return TestHttpServer.Odpowiedz.ok("{\"error\":6,\"message\":\"not found\"}");
            }
            return new TestHttpServer.Odpowiedz(503, "{}");
        });
        backfill = serwis(new LastFmService("klucz", lastFmServer.url() + "/2.0/", 2000));
        ala = users.save(new User("ala", "ala@example.com", "hash"));
    }

    @AfterEach
    void tearDown() {
        lastFmServer.close();
    }

    private ArtistGenreBackfillService serwis(LastFmService lastFm) {
        return new ArtistGenreBackfillService(lastFm, artists, transactionManager,
            Clock.fixed(EventImportServiceTest.TERAZ, ZoneOffset.UTC), 0);
    }

    private Artist ulubiony(String id, String nazwa) {
        Artist a = artists.save(new Artist(id, nazwa, null));
        ala.getFavoriteArtists().add(a);
        entityManager.flush();
        return a;
    }

    private Artist odswiez(Artist a) {
        entityManager.flush();
        entityManager.clear();
        return artists.findById(a.getId()).orElseThrow();
    }

    @Test
    @DisplayName("ulubiony bez gatunkow dostaje je z Last.fm")
    void fillsMissingGenres() {
        Artist radiohead = ulubiony("1", "Radiohead");

        assertThat(backfill.backfill()).isEqualTo(1);

        Artist po = odswiez(radiohead);
        assertThat(po.getGenres()).containsExactlyInAnyOrder("alternative", "rock");
        assertThat(po.getGenresCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("artysta, ktorego Last.fm nie zna, jest oznaczony jako sprawdzony - i nie wraca od razu")
    void unknownArtistIsCheckedOnce() {
        Artist nieznany = ulubiony("2", "Nikomu Nieznany");

        backfill.backfill();
        lastFmServer.requests().clear();
        backfill.backfill();

        assertThat(odswiez(nieznany).getGenresCheckedAt()).isNotNull();
        assertThat(lastFmServer.requests()).isEmpty();
    }

    @Test
    @DisplayName("po dwoch miesiacach pytamy znowu - moze tagi juz sa")
    void recheckedAfterTwoMonths() {
        Artist nieznany = ulubiony("2", "Nikomu Nieznany");
        backfill.backfill();
        lastFmServer.requests().clear();

        ArtistGenreBackfillService pozniej = new ArtistGenreBackfillService(
            new LastFmService("klucz", lastFmServer.url() + "/2.0/", 2000), artists, transactionManager,
            Clock.fixed(EventImportServiceTest.TERAZ.plus(Duration.ofDays(61)), ZoneOffset.UTC), 0);
        pozniej.backfill();

        assertThat(lastFmServer.requests()).hasSize(1);
        assertThat(odswiez(nieznany).getGenresCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("awaria Last.fm nie oznacza artysty jako sprawdzonego")
    void failureLeavesArtistUnchecked() {
        Artist awaria = ulubiony("3", "Awaria");

        backfill.backfill();

        assertThat(odswiez(awaria).getGenresCheckedAt()).isNull();
    }

    @Test
    @DisplayName("artysci z gatunkami i ci, ktorych nikt nie ma w ulubionych, sa pomijani")
    void skipsArtistsWithGenresAndOrphans() {
        Artist zGatunkami = new Artist("4", "Radiohead", null);
        zGatunkami.applyGenres(Set.of("rock"));
        artists.save(zGatunkami);
        ala.getFavoriteArtists().add(zGatunkami);
        artists.save(new Artist("5", "Radiohead", null));   // niczyj
        entityManager.flush();

        assertThat(backfill.backfill()).isZero();
        assertThat(lastFmServer.requests()).isEmpty();
    }

    @Test
    @DisplayName("bez klucza Last.fm nic sie nie dzieje")
    void withoutKey() {
        ulubiony("1", "Radiohead");

        assertThat(serwis(new LastFmService("", lastFmServer.url() + "/2.0/", 2000)).backfill()).isZero();
        assertThat(lastFmServer.requests()).isEmpty();
    }
}
