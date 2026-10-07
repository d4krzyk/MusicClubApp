package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.MusicEventRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Opis wykonawcy od Ticketmastera w "Kim jest?" przez prawdziwe API, z udawanym Ticketmasterem: bierzemy go z importu
 * wydarzen, gdy przyszedl (sprawdzone na zywo, ze zwykle nie przychodzi - "About" z ticketmaster.pl nie jest w publicznym
 * API), a o wykonawcow osobno Ticketmastera nie pytamy. Do tego opis wydarzenia ze wszystkich pol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Wykonawcy - opis od Ticketmastera")
class ArtistAboutFlowTest {

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
        rejestr.add("app.ticketmaster.base-url", () -> server.url() + "/discovery/v2");
        rejestr.add("app.ticketmaster.api-key", () -> "tm-klucz-testowy");
        // import w tle nie ma ruszyc w polowie testu - test wola go sam
        rejestr.add("app.events.import.initial-delay-ms", () -> 3_600_000);
        rejestr.add("app.favorites.genres.initial-delay-ms", () -> 3_600_000);
    }

    @AfterAll
    static void closeServer() {
        server.close();
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private EventImportService importer;
    @Autowired private MusicEventRepository events;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private volatile String opisSupportu = "Duet z Gdańska grający dream pop.";
    private volatile String opisKovacs = "<p>Sharon Kovacs is a Dutch singer-songwriter known for her soulful voice.</p>";

    private String wydarzenia() throws IOException {
        String opis = opisKovacs == null ? "" : ", \"description\": " + json.writeValueAsString(opisKovacs);
        String support = opisSupportu == null ? "" : ", \"additionalInfo\": " + json.writeValueAsString(opisSupportu);
        return """
            { "_embedded": { "events": [ {
                "id": "KOV1", "name": "Kovacs", "locale": "pl-pl",
                "dates": { "start": { "localDate": "%s", "localTime": "20:00:00" } },
                "info": "Kovacs wraca do Polski z nową płytą.",
                "additionalInfo": "<p>Bilety kolekcjonerskie <b>tylko</b> w kasie klubu.</p>",
                "_embedded": {
                  "venues": [ { "id": "V1", "name": "Progresja", "city": { "name": "Warszawa" },
                                "country": { "countryCode": "PL" } } ],
                  "attractions": [
                    { "id": "TMKOVACS", "name": "Kovacs", "locale": "en-us",
                      "url": "https://www.ticketmaster.pl/artist/kovacs-tickets/950040" %s },
                    { "id": "TMBRAK", "name": "Support Bez Opisu", "locale": "pl-pl" },
                    { "id": "TMSUPPORT", "name": "Support Duo", "locale": "pl-pl" %s } ] } } ] },
              "page": { "size": 200, "totalElements": 1, "totalPages": 1, "number": 0 } }
            """.formatted(LocalDate.now().plusDays(30), opis, support);
    }

    @BeforeEach
    void setUp() {
        server.odpowiadaj("/discovery/v2/events.json", zapytanie -> {
            try {
                return TestHttpServer.Odpowiedz.ok(wydarzenia());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        users.save(new User("ab_ala", "ab_ala@example.com", "x"));
        assertThat(importer.runImport().success()).isTrue();
        em.flush();
        em.clear();
    }

    private JsonNode profil(String nazwa) throws Exception {
        return json.readTree(mvc.perform(get("/api/artists/profile").param("name", nazwa).param("lang", "pl")
                .with(user("ab_ala")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("opis z importu: od razu w \"Kim jest?\", bez pytania Ticketmastera; jezyk i strona artysty")
    void aboutFromImport() throws Exception {
        JsonNode p = profil("Kovacs");
        assertThat(p.get("about").asText())
            .isEqualTo("Sharon Kovacs is a Dutch singer-songwriter known for her soulful voice.");
        assertThat(p.get("aboutLang").asText()).isEqualTo("en");
        assertThat(p.get("aboutUrl").asText()).isEqualTo("https://www.ticketmaster.pl/artist/kovacs-tickets/950040");
        assertThat(p.get("bio").isNull()).as("bez klucza Last.fm").isTrue();
    }

    @Test
    @DisplayName("opis wydarzenia ze wszystkich pol Ticketmastera, bez HTML-a")
    void eventDescription() throws Exception {
        MusicEvent kovacs = events.findByExternalIdIn(List.of("KOV1")).get(0);
        JsonNode w = json.readTree(mvc.perform(get("/api/events/" + kovacs.getId()).with(user("ab_ala")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(w.get("description").asText())
            .isEqualTo("Kovacs wraca do Polski z nową płytą.\n\nBilety kolekcjonerskie tylko w kasie klubu.");

        // Opis zapisany przed odsiewaniem formulek (stary import) - strona i tak ich nie pokazuje
        jdbc.update("UPDATE music_events SET description = ?, please_note = ? WHERE id = ?",
            TicketBoilerplateTest.LIVE_NATION + "\n\nKoncert promuje nową płytę.", TicketBoilerplateTest.STODOLA,
            kovacs.getId());
        em.clear();
        JsonNode stary = json.readTree(mvc.perform(get("/api/events/" + kovacs.getId()).with(user("ab_ala")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(stary.get("description").asText()).isEqualTo("Koncert promuje nową płytę.");
        assertThat(stary.get("pleaseNote").isNull()).as("same formulki - bez uwag").isTrue();
    }

    @Test
    @DisplayName("wykonawca bez opisu w imporcie: pusto, a Ticketmastera o niego osobno nie pytamy (takze przy kolejnych klikach)")
    void missingAboutIsNotAskedFor() throws Exception {
        JsonNode p = profil("Support Bez Opisu");
        assertThat(p.get("about").isNull()).isTrue();
        assertThat(p.get("aboutLang").isNull()).isTrue();
        assertThat(p.get("aboutUrl").isNull()).isTrue();
        profil("support bez opisu");
        jdbc.update("UPDATE performer_about SET checked_at = ?", LocalDateTime.now().minusDays(90));
        em.clear();
        profil("Kovacs");
        assertThat(server.requests()).as("tylko lista wydarzen z importu, zadnych /attractions/")
            .allMatch(zapytanie -> zapytanie.contains("/events.json"));
    }

    @Test
    @DisplayName("opis z importu: kolejny import bez opisu go nie kasuje, a zmieniony poprawia; jezyk z locale wykonawcy")
    void importKeepsAndUpdatesAbout() throws Exception {
        JsonNode support = profil("Support Duo");
        assertThat(support.get("about").asText()).isEqualTo("Duet z Gdańska grający dream pop.");
        assertThat(support.get("aboutLang").asText()).isEqualTo("pl");
        assertThat(support.get("aboutUrl").isNull()).as("bez strony artysty w odpowiedzi").isTrue();

        // Import znow, tym razem bez opisu Supportu - zostaje; opis Kovacs zmieniony - poprawiony
        opisSupportu = null;
        opisKovacs = "Nowy opis Kovacs.";
        assertThat(importer.runImport().success()).isTrue();
        em.flush();
        em.clear();
        assertThat(profil("Support Duo").get("about").asText()).isEqualTo("Duet z Gdańska grający dream pop.");
        assertThat(profil("Kovacs").get("about").asText()).isEqualTo("Nowy opis Kovacs.");
    }
}
