package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.PerformerLinkKind;
import com.musicclubapp.entity.PerformerTags;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistProfileRepository;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.PerformerTagsRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Kim jest" wykonawca i sklad na stronie wydarzenia przez prawdziwe API, z udawanym Last.fm: opis w jezyku interfejsu,
 * zapamietany na 30 dni, angielski zamiast brakujacego polskiego, awaria nie zapamietana, linki z importu, limit zapytan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Wykonawcy - opis i sklad wydarzenia")
class ArtistProfileFlowTest {

    static final TestHttpServer server;

    static {
        try {
            server = new TestHttpServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry rejestr) {
        rejestr.add("app.lastfm.base-url", () -> server.url() + "/2.0/");
        rejestr.add("app.lastfm.api-key", () -> "klucz-testowy");
        rejestr.add("app.artists.profiles-per-minute", () -> 8);
        // gatunki ulubionych w tle nie maja pytac udawanego serwera w polowie testu
        rejestr.add("app.favorites.genres.initial-delay-ms", () -> 3_600_000);
    }

    @AfterAll
    static void closeServer() {
        server.close();
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private MusicEventRepository events;
    @Autowired private ArtistRepository artists;
    @Autowired private PerformerTagsRepository tags;
    @Autowired private PerformerTagService performerTags;
    @Autowired private ArtistProfileRepository profiles;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    /** Ile razy pytano o opis i co ma odpowiadac Last.fm (po jezyku). */
    private final AtomicInteger zapytan = new AtomicInteger();
    private volatile String polski = LastFmArtistInfoTest.OPIS;
    private volatile String angielski = LastFmArtistInfoTest.OPIS.replace("Kult &ndash; polski", "Kult is a Polish");
    private MusicEvent koncert;

    @BeforeEach
    void setUp() {
        server.odpowiadaj("/2.0/", zapytanie -> {
            if (!zapytanie.contains("method=artist.getinfo")) {
                return new TestHttpServer.Odpowiedz(404, "{}");
            }
            zapytan.incrementAndGet();
            String odp = zapytanie.contains("lang=pl") ? polski : angielski;
            return odp == null ? new TestHttpServer.Odpowiedz(500, "awaria") : TestHttpServer.Odpowiedz.ok(odp);
        });
        User ala = users.save(new User("ap_ala", "ap_ala@example.com", "x"));
        Artist kult = new Artist("ap1", "KULT", null);
        ala.getFavoriteArtists().add(artists.save(kult));
        users.save(ala);
        koncert = events.save(WydarzeniaTestowe.wydarzenie("APK", "Kult i goscie", LocalDate.now().plusDays(10),
            "Warszawa", "Rock", null, "Kult", "Zespol bez opisu"));
        PerformerTags t = new PerformerTags(NameKeys.of("Kult"), "Kult");
        t.update(Set.of("polish rock"), LocalDateTime.now());
        tags.save(t);
        // linki - tak, jak zapisuje je import
        var e = new TicketmasterClient.Event("APK", "Kult i goscie", LocalDate.now().plusDays(10), null, null, null,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(new TicketmasterClient.Performer("A1", "Kult",
                Map.of(PerformerLinkKind.SPOTIFY, "https://open.spotify.com/artist/kult",
                    PerformerLinkKind.HOMEPAGE, "https://kult.art.pl/"))),
            "PL", TicketmasterClient.Organizer.NONE);
        performerTags.saveLinks(List.of(e));
        em.flush();
    }

    private ResultActions profil(String nazwa, String jezyk) throws Exception {
        return profil(nazwa, jezyk, "ap_ala");
    }

    /** Limit liczy sie na osobe i zyje dluzej niz jeden test - test limitu pyta jako ktos inny. */
    private ResultActions profil(String nazwa, String jezyk, String kto) throws Exception {
        return mvc.perform(get("/api/artists/profile").param("name", nazwa).param("lang", jezyk)
            .with(user(kto)).header("Accept-Language", "pl"));
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("opis po polsku z Last.fm, zapamietany: drugie pytanie nie idzie do Last.fm; linki z importu")
    void profileCached() throws Exception {
        JsonNode p = tresc(profil("Kult", "pl").andExpect(status().isOk()));
        assertThat(p.get("bio").asText()).startsWith("Kult – polski zespół");
        assertThat(p.get("bioUrl").asText()).isEqualTo("https://www.last.fm/music/Kult");
        assertThat(p.get("listeners").asLong()).isEqualTo(312877L);
        assertThat(p.get("similar")).hasSize(5);
        assertThat(p.get("links").get(0).get("kind").asText()).isEqualTo("HOMEPAGE");
        assertThat(p.get("links").get(1).get("url").asText()).isEqualTo("https://open.spotify.com/artist/kult");
        assertThat(zapytan.get()).isEqualTo(1);

        em.flush();
        tresc(profil("KULT", "pl").andExpect(status().isOk()));
        assertThat(zapytan.get()).as("z pamieci, inna wielkosc liter to ten sam wykonawca").isEqualTo(1);
        JsonNode en = tresc(profil("Kult", "en").andExpect(status().isOk()));
        assertThat(en.get("bio").asText()).startsWith("Kult is a Polish");
        assertThat(zapytan.get()).as("inny jezyk - osobny opis").isEqualTo(2);
    }

    @Test
    @DisplayName("brak opisu po polsku - angielski; awaria nie jest zapamietywana; po 30 dniach stary opis przy awarii")
    void fallbackAndFailure() throws Exception {
        polski = LastFmArtistInfoTest.OPIS.replaceAll("\"summary\": \"[^\\n]*\"", "\"summary\": \"\"");
        JsonNode p = tresc(profil("Kult", "pl").andExpect(status().isOk()));
        assertThat(p.get("bio").asText()).startsWith("Kult is a Polish");
        assertThat(zapytan.get()).isEqualTo(2);
        em.flush();

        // Awaria Last.fm przy pierwszym pytaniu: same linki, nic nie zapisane
        polski = null;
        angielski = null;
        JsonNode bez = tresc(profil("Zespol bez opisu", "pl").andExpect(status().isOk()));
        assertThat(bez.get("bio").isNull()).isTrue();
        assertThat(bez.get("links")).isEmpty();
        assertThat(profiles.findByNameKeyAndLang(NameKeys.of("Zespol bez opisu"), "pl")).isEmpty();

        // Przeterminowany opis i awaria - zostaje stary
        jdbc.update("UPDATE artist_profiles SET fetched_at = ?", LocalDateTime.now().minusDays(31));
        em.clear();
        JsonNode stary = tresc(profil("Kult", "pl").andExpect(status().isOk()));
        assertThat(stary.get("bio").asText()).startsWith("Kult is a Polish");
    }

    @Test
    @DisplayName("tylko wykonawcy z koncertow (404 dla dowolnej nazwy); limit zapytan na osobe")
    void onlyPerformersAndLimit() throws Exception {
        profil("Ktos Spoza Koncertow", "pl").andExpect(status().isNotFound());
        profil("   ", "pl").andExpect(status().isNotFound());
        assertThat(zapytan.get()).isZero();
        users.save(new User("ap_limit", "ap_limit@example.com", "x"));
        users.save(new User("ap_inny", "ap_inny@example.com", "x"));
        em.flush();
        for (int i = 0; i < 8; i++) {
            profil("Kult", "pl", "ap_limit").andExpect(status().isOk());
        }
        profil("Kult", "pl", "ap_limit").andExpect(status().isTooManyRequests());
        profil("Kult", "pl", "ap_inny").andExpect(status().isOk());
    }

    @Test
    @DisplayName("strona wydarzenia: sklad z gatunkami, linkami i zaznaczonym ulubionym; dane od organizatora")
    void eventDetailsLineup() throws Exception {
        koncert.organizer("Wejscie od 18:00", "Agencja X", 99.0, 149.0, "PLN", true,
            Instant.now().plusSeconds(86400), "Podjazd od ul. Glownej");
        events.save(koncert);
        em.flush();
        JsonNode w = tresc(mvc.perform(get("/api/events/" + koncert.getId()).with(user("ap_ala")))
            .andExpect(status().isOk()));
        JsonNode kult = w.get("lineup").get(0);
        assertThat(kult.get("name").asText()).isEqualTo("Kult");
        assertThat(kult.get("favorite").asBoolean()).isTrue();
        assertThat(kult.get("tags").get(0).asText()).isEqualTo("polish rock");
        assertThat(kult.get("links")).hasSize(2);
        JsonNode drugi = w.get("lineup").get(1);
        assertThat(drugi.get("favorite").asBoolean()).isFalse();
        assertThat(drugi.get("tags")).isEmpty();
        assertThat(drugi.get("links")).isEmpty();
        assertThat(w.get("pleaseNote").asText()).isEqualTo("Wejscie od 18:00");
        assertThat(w.get("promoter").asText()).isEqualTo("Agencja X");
        assertThat(w.get("priceMin").asDouble()).isEqualTo(99.0);
        assertThat(w.get("priceCurrency").asText()).isEqualTo("PLN");
        assertThat(w.get("ageRestricted").asBoolean()).isTrue();
        assertThat(w.get("accessibility").asText()).isEqualTo("Podjazd od ul. Glownej");
        assertThat(zapytan.get()).as("strona wydarzenia nie pyta Last.fm").isZero();
    }

    @Test
    @DisplayName("import: nowe linki dopisane, zmienione poprawione, brakujace zostaja")
    void linksUpsert() {
        var e = new TicketmasterClient.Event("APK", "Kult i goscie", LocalDate.now().plusDays(10), null, null, null,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(new TicketmasterClient.Performer("A1", "kult",
                Map.of(PerformerLinkKind.SPOTIFY, "https://open.spotify.com/artist/nowy",
                    PerformerLinkKind.INSTAGRAM, "https://instagram.com/kult"))),
            "PL", TicketmasterClient.Organizer.NONE);
        performerTags.saveLinks(List.of(e));
        em.flush();
        var linki = performerTags.linksOf(List.of(NameKeys.of("Kult"))).get(NameKeys.of("Kult"));
        assertThat(linki).extracting(l -> l.kind() + " " + l.url()).containsExactly(
            "HOMEPAGE https://kult.art.pl/",
            "SPOTIFY https://open.spotify.com/artist/nowy",
            "INSTAGRAM https://instagram.com/kult");
    }
}
