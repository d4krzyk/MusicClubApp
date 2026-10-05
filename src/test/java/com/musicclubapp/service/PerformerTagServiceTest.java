package com.musicclubapp.service;

import com.musicclubapp.entity.PerformerTags;
import com.musicclubapp.repository.PerformerTagsRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Gatunki wykonawcow z koncertow - z Last.fm, zapamietywane. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Gatunki wykonawcow z Last.fm")
class PerformerTagServiceTest {

    private static final String TAGI_KULTU = """
        {"toptags":{"tag":[
          {"name":"punk rock","count":100},
          {"name":"polish","count":90},
          {"name":"rock","count":70},
          {"name":"cos-niszowego","count":2}
        ]}}
        """;

    private static final String NIE_MA_ARTYSTY =
        "{\"error\":6,\"message\":\"The artist you supplied could not be found\"}";

    @Autowired private PerformerTagsRepository repository;
    @Autowired private com.musicclubapp.repository.PerformerLinkRepository links;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestHttpServer lastFmServer;
    private PerformerTagService tagi;

    @BeforeEach
    void setUp() throws IOException {
        lastFmServer = new TestHttpServer();
        lastFmServer.odpowiadaj("/2.0/", query -> {
            if (query.contains("artist=Kult") || query.contains("artist=KULT")) {
                return TestHttpServer.Odpowiedz.ok(TAGI_KULTU);
            }
            if (query.contains("artist=Nieznany")) {
                return TestHttpServer.Odpowiedz.ok(NIE_MA_ARTYSTY);
            }
            return new TestHttpServer.Odpowiedz(500, "{}");
        });
        tagi = serwis(new LastFmService("klucz-lastfm", lastFmServer.url() + "/2.0/", 2000));
    }

    @AfterEach
    void tearDown() {
        lastFmServer.close();
    }

    private PerformerTagService serwis(LastFmService lastFm) {
        return new PerformerTagService(lastFm, repository, links, transactionManager,
            Clock.fixed(EventImportServiceTest.TERAZ, ZoneOffset.UTC), 0);
    }

    @Test
    @DisplayName("sprawdza nieznanych wykonawcow; ta sama nazwa inaczej zapisana to jedno zapytanie")
    void fetchesUnknownOnce() {
        int sprawdzonych = tagi.refresh(List.of("Kult", "KULT", "Nieznany"));

        assertThat(sprawdzonych).isEqualTo(2);
        assertThat(lastFmServer.requests()).hasSize(2);
        // "polish" to nie gatunek, a tag z liczba 2 jest ponizej progu
        assertThat(tagi.tagsOf(List.of("kult")).get("kult")).containsExactlyInAnyOrder("punkrock", "rock");
        // Last.fm nie zna artysty - to tez odpowiedz, zapamietana z pusta lista
        assertThat(repository.findById("nieznany")).map(PerformerTags::getGenres).hasValue(java.util.Set.of());
    }

    @Test
    @DisplayName("znanych nie sprawdza drugi raz")
    void doesNotRefetchKnown() {
        tagi.refresh(List.of("Kult", "Nieznany"));
        lastFmServer.requests().clear();

        tagi.refresh(List.of("Kult", "Nieznany"));

        assertThat(lastFmServer.requests()).isEmpty();
    }

    @Test
    @DisplayName("awaria Last.fm nie zapisuje pustych gatunkow - wykonawca czeka na nastepny raz")
    void failureIsNotRemembered() {
        tagi.refresh(List.of("Awaria"));

        assertThat(repository.findById("awaria")).isEmpty();
    }

    @Test
    @DisplayName("po pieciu bledach z rzedu przestaje pytac - Last.fm najwyrazniej lezy")
    void stopsAfterRepeatedFailures() {
        List<String> nazwy = IntStream.range(0, 20).mapToObj(i -> "Awaria " + i).toList();

        tagi.refresh(nazwy);

        assertThat(lastFmServer.requests()).hasSize(5);
    }

    @Test
    @DisplayName("bez klucza Last.fm nic sie nie dzieje")
    void withoutKey() {
        PerformerTagService bezKlucza = serwis(new LastFmService("", lastFmServer.url() + "/2.0/", 2000));

        assertThat(bezKlucza.refresh(List.of("Kult"))).isZero();
        assertThat(bezKlucza.available()).isFalse();
        assertThat(lastFmServer.requests()).isEmpty();
    }
}
