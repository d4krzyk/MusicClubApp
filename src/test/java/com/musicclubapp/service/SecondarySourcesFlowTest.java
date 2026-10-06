package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventSourceEntry;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.EventSourceEntryRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Wiele zrodel na prawdziwej bazie: Bandsintown uzupelnia koncert z Ticketmastera i zaklada brakujacy, Ticketmaster
 * przejmuje go, gdy sam pokaze, a wycofany z Ticketmastera przechodzi na inne zrodlo; po 3 dniach niewidzenia
 * wydarzenie ze zrodla pobocznego znika albo jest wycofywane. Udawane serwery zamiast Ticketmastera i Bandsintown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Wiele zrodel wydarzen - laczenie i uzupelnianie brakow")
class SecondarySourcesFlowTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MusicEventRepository events;
    @Autowired private EventSourceEntryRepository sources;
    @Autowired private EventParticipationRepository participations;
    @Autowired private PerformerTagService performerTags;
    @Autowired private UserRepository users;
    @Autowired private ArtistRepository artists;
    @Autowired private EventMerger merger;
    @Autowired private EventImportService importer;
    @Autowired private PlatformTransactionManager tx;
    @Autowired private EntityManager em;
    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private TestHttpServer server;
    private LocalDate dzien;
    private final Map<String, String> bit = new java.util.HashMap<>();
    private final List<String> tm = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        dzien = importer.today().plusDays(20);
        User fan = users.save(new User("zr_fan", "zr_fan@example.com", "x"));
        Artist kult = artists.save(new Artist("dz-kult", "Kult", null));
        Artist hey = artists.save(new Artist("dz-hey", "Hey", null));
        fan.getFavoriteArtists().add(kult);
        fan.getFavoriteArtists().add(hey);
        users.save(fan);
        Function<String, TestHttpServer.Odpowiedz> odp = q -> TestHttpServer.Odpowiedz.ok("[]");
        server.odpowiadaj("/artists/Kult/events", q -> TestHttpServer.Odpowiedz.ok(bit.getOrDefault("Kult", "[]")));
        server.odpowiadaj("/artists/Hey/events", q -> TestHttpServer.Odpowiedz.ok(bit.getOrDefault("Hey", "[]")));
        server.odpowiadaj("/discovery/v2/events.json", q -> TestHttpServer.Odpowiedz.ok(
            "{\"_embedded\":{\"events\":[" + String.join(",", tm) + "]},\"page\":{\"size\":200,\"totalElements\":"
                + tm.size() + ",\"totalPages\":1,\"number\":0}}"));
        em.flush();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private SecondaryEventImportService wtorne() {
        return new SecondaryEventImportService(new BandsintownClient("app", server.url(), 2000),
            new SongkickClient("klucz-sk", server.url() + "/api/3.0", 2000), merger, importer, events, sources,
            participations, artists, tx, Clock.systemUTC(), 150, 0, 86_400_000L, "52.2297,21.0122");
    }

    private EventImportService ticketmaster() {
        return new EventImportService(new TicketmasterClient("klucz", server.url() + "/discovery/v2", 2000), events,
            participations, performerTags, users, merger, tx, Clock.systemUTC(), 0, 21_600_000L, 1000);
    }

    private static String bitEvent(String id, LocalDate d, String godz, String sala, String miasto, String kraj,
                                   String lat, String lon, String... sklad) {
        return """
            {"id":"%s","url":"https://www.bandsintown.com/e/%s","datetime":"%sT%s:00","title":"",
             "venue":{"name":"%s","latitude":%s,"longitude":%s,"city":"%s","country":"%s"},
             "offers":[{"type":"Tickets","url":"https://bilety.example/%s"}],"lineup":[%s]}
            """.formatted(id, id, d, godz, sala, lat == null ? "null" : "\"" + lat + "\"", lon == null ? "null" : "\"" + lon + "\"",
            miasto, kraj, id, String.join(",", java.util.Arrays.stream(sklad).map(s -> "\"" + s + "\"").toList()));
    }

    private static String tmEvent(String id, String nazwa, LocalDate d, String godz, String sala, String miasto,
                                  String... sklad) {
        StringBuilder atrakcje = new StringBuilder();
        for (String s : sklad) {
            atrakcje.append(atrakcje.length() == 0 ? "" : ",").append("{\"id\":\"A-").append(s).append("\",\"name\":\"")
                .append(s).append("\"}");
        }
        return """
            {"id":"%s","name":"%s","type":"event","url":"https://www.ticketmaster.pl/event/%s",
             "dates":{"start":{"localDate":"%s"%s},"status":{"code":"onsale"}},
             "classifications":[{"primary":true,"genre":{"name":"Rock"}}],
             "_embedded":{"venues":[{"name":"%s","id":"V-%s","city":{"name":"%s"},"country":{"countryCode":"PL"}}],
                          "attractions":[%s]}}
            """.formatted(id, nazwa, id, d, godz == null ? "" : ",\"localTime\":\"" + godz + ":00\"", sala, sala, miasto,
            atrakcje);
    }

    /** Nasze wydarzenie z Ticketmastera: Kult w Progresji, bez godziny i bez miejsca na mapie. */
    private MusicEvent kultZTicketmastera() {
        MusicEvent e = WydarzeniaTestowe.wydarzenie("TMKULT", "Kult", dzien, "Warszawa", "Rock", null, "Kult");
        e.place("V-Progresja", "Progresja", "Warszawa", EventImportService.cityKey("Warszawa"), null, null, null);
        e.schedule(dzien, null);
        e.link("https://www.ticketmaster.pl/event/TMKULT", null, null);
        e.markSeen(LocalDateTime.now());
        MusicEvent zapisane = events.save(e);
        em.flush();
        return zapisane;
    }

    private MusicEvent wydarzenie(String externalId) {
        return events.findAll().stream().filter(e -> e.getExternalId().equals(externalId)).findFirst().orElse(null);
    }

    /* ---------------------------- testy ---------------------------- */

    @Test
    @DisplayName("Bandsintown: ten sam koncert uzupelnia braki (bez nadpisywania), brakujacy dostaje karte, obcy kraj pominiety")
    void bandsintownFillsAndAdds() throws Exception {
        MusicEvent kult = kultZTicketmastera();
        bit.put("Kult", "[" + bitEvent("1001", dzien, "20:00", "Klub Progresja", "Warszawa", "Poland", "52.2425", "20.9332", "Kult") + "]");
        bit.put("Hey", "[" + bitEvent("2001", dzien.plusDays(1), "21:00", "Stodoła", "Warszawa", "Poland", null, null, "Hey")
            + "," + bitEvent("2002", dzien.plusDays(2), "20:00", "SO36", "Berlin", "Germany", null, null, "Hey") + "]");

        SecondaryEventImportService.Result r = wtorne().runBandsintown();
        assertThat(r.seen()).as("bez Berlina - kraju nikt nie wybral").isEqualTo(2);
        em.flush();
        em.clear();

        MusicEvent po = events.findById(kult.getId()).orElseThrow();
        assertThat(po.getStartTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(po.getLatitude()).isEqualTo(52.2425);
        assertThat(po.getVenueName()).as("nazwa z glownego zrodla zostaje").isEqualTo("Progresja");
        assertThat(po.getSource()).isEqualTo(EventSource.TICKETMASTER);
        assertThat(po.getTicketUrl()).as("bilety Ticketmastera zostaja").isEqualTo("https://www.ticketmaster.pl/event/TMKULT");

        MusicEvent hey = wydarzenie("bandsintown:2001");
        assertThat(hey).isNotNull();
        assertThat(hey.getSource()).isEqualTo(EventSource.BANDSINTOWN);
        assertThat(hey.getName()).isEqualTo("Hey");
        assertThat(hey.getVenueName()).isEqualTo("Stodoła");
        assertThat(hey.getStartTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(hey.getCountryCode()).isEqualTo("PL");
        assertThat(hey.getTicketUrl()).isEqualTo("https://bilety.example/2001");
        assertThat(events.count()).isEqualTo(2);
        assertThat(sources.findAll()).hasSize(2);

        // podpis na stronie wydarzenia: glowne zrodlo, potem Bandsintown z odnosnikiem
        JsonNode szczegoly = JSON.readTree(mvc.perform(get("/api/events/" + kult.getId()).with(user("zr_fan")))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(szczegoly.get("sources").toString())
            .isEqualTo("[{\"name\":\"Ticketmaster\",\"url\":null},{\"name\":\"Bandsintown\",\"url\":\"https://www.bandsintown.com/e/1001\"}]");

        // drugi przebieg - nic nowego, wpisy tylko odswiezone
        wtorne().runBandsintown();
        em.flush();
        assertThat(events.count()).isEqualTo(2);
        assertThat(sources.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Ticketmaster: braki zostaja uzupelnione po jego imporcie, koncert z Bandsintown przejmuje, wycofany oddaje")
    void ticketmasterAdoptsAndRescues() {
        MusicEvent kult = kultZTicketmastera();
        bit.put("Kult", "[" + bitEvent("1001", dzien, "20:00", "Klub Progresja", "Warszawa", "Poland", "52.2425", "20.9332", "Kult") + "]");
        bit.put("Hey", "[" + bitEvent("2001", dzien.plusDays(1), "21:00", "Stodoła", "Warszawa", "Poland", null, null, "Hey") + "]");
        wtorne().runBandsintown();
        em.flush();
        Long heyId = wydarzenie("bandsintown:2001").getId();

        // Ticketmaster pokazuje oba (Kult bez godziny, Hey pierwszy raz) - bez duplikatow, braki uzupelnione
        tm.add(tmEvent("TMKULT", "Kult", dzien, null, "Progresja", "Warsaw", "Kult"));
        tm.add(tmEvent("TMHEY", "Hey", dzien.plusDays(1), "21:00", "Stodoła", "Warsaw", "Hey"));
        ticketmaster().runImport();
        em.flush();
        em.clear();
        assertThat(events.count()).as("ten sam koncert to jedna karta").isEqualTo(2);
        MusicEvent kultPo = events.findById(kult.getId()).orElseThrow();
        assertThat(kultPo.getStartTime()).as("Ticketmaster nie zna godziny - zostaje z Bandsintown").isEqualTo(LocalTime.of(20, 0));
        assertThat(kultPo.getLatitude()).isEqualTo(52.2425);
        MusicEvent heyPo = events.findById(heyId).orElseThrow();
        assertThat(heyPo.getSource()).isEqualTo(EventSource.TICKETMASTER);
        assertThat(heyPo.getExternalId()).isEqualTo("TMHEY");
        assertThat(heyPo.getVenueExternalId()).as("dane Ticketmastera").isEqualTo("V-Stodoła");

        // Ticketmaster przestaje pokazywac Kult - Bandsintown widzial go przed chwila, wiec wydarzenie zostaje
        tm.clear();
        tm.add(tmEvent("TMHEY", "Hey", dzien.plusDays(1), "21:00", "Stodoła", "Warsaw", "Hey"));
        ticketmaster().runImport();
        em.flush();
        em.clear();
        MusicEvent uratowany = events.findById(kult.getId()).orElseThrow();
        assertThat(uratowany.getSource()).isEqualTo(EventSource.BANDSINTOWN);
        assertThat(uratowany.getExternalId()).isEqualTo("bandsintown:1001");
        assertThat(uratowany.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("po 3 dniach niewidzenia: wydarzenie z Bandsintown znika, z zapisami - jest wycofywane; przy zerze wynikow nic")
    void staleSecondary() {
        bit.put("Hey", "[" + bitEvent("2001", dzien.plusDays(1), "21:00", "Stodoła", "Warszawa", "Poland", null, null, "Hey")
            + "," + bitEvent("2003", dzien.plusDays(3), "21:00", "Stodoła", "Warszawa", "Poland", null, null, "Hey") + "]");
        bit.put("Kult", "[" + bitEvent("1009", dzien.plusDays(5), "20:00", "Progresja", "Warszawa", "Poland", null, null, "Kult") + "]");
        wtorne().runBandsintown();
        em.flush();
        MusicEvent zapisy = wydarzenie("bandsintown:2001");
        MusicEvent bezZapisow = wydarzenie("bandsintown:2003");
        participations.save(new com.musicclubapp.entity.EventParticipation(zapisy, users.findByUsername("zr_fan").orElseThrow(),
            ParticipationStatus.GOING, false, LocalDateTime.now()));
        em.flush();
        String stare = "UPDATE music_events SET last_seen_at = now() - interval '4' day WHERE external_id IN ('bandsintown:2001', 'bandsintown:2003')";
        jdbc.update(stare);
        jdbc.update("UPDATE event_sources SET seen_at = now() - interval '4' day WHERE external_id IN ('2001', '2003')");
        em.clear();

        // Bandsintown nic nie oddaje - bezpiecznik: nic nie znika
        bit.clear();
        assertThat(wtorne().runBandsintown().removed()).isZero();
        em.flush();
        assertThat(events.findById(bezZapisow.getId())).isPresent();

        // Bandsintown pokazuje inne koncerty, a tych dwoch juz nie
        bit.put("Kult", "[" + bitEvent("1009", dzien.plusDays(5), "20:00", "Progresja", "Warszawa", "Poland", null, null, "Kult") + "]");
        SecondaryEventImportService.Result r = wtorne().runBandsintown();
        em.flush();
        em.clear();
        assertThat(r.removed()).isEqualTo(1);
        assertThat(events.findById(bezZapisow.getId())).isEmpty();
        assertThat(events.findById(zapisy.getId()).orElseThrow().isWithdrawn()).as("ktos sie zapisal - wycofane").isTrue();
        // wraca, gdy Bandsintown znow je pokaze
        bit.put("Hey", "[" + bitEvent("2001", dzien.plusDays(1), "21:00", "Stodoła", "Warszawa", "Poland", null, null, "Hey") + "]");
        wtorne().runBandsintown();
        em.flush();
        em.clear();
        assertThat(events.findById(zapisy.getId()).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("Songkick: okolica miasta, ten sam koncert uzupelnia, festiwal dostaje karte, odwolany zostaje odwolany")
    void songkick() {
        MusicEvent kult = kultZTicketmastera();
        server.odpowiadaj("/api/3.0/events.json", q -> TestHttpServer.Odpowiedz.ok("""
            { "resultsPage": { "status": "ok", "page": 1, "perPage": 50, "totalEntries": 3, "results": { "event": [
              { "id": 501, "type": "Concert", "status": "ok", "displayName": "Kult at Progresja",
                "uri": "https://www.songkick.com/concerts/501", "start": { "date": "%s", "time": "19:30:00" },
                "performance": [ { "artist": { "displayName": "Kult" } } ],
                "venue": { "displayName": "Progresja Music Zone", "lat": 52.2425, "lng": 20.9332,
                           "metroArea": { "displayName": "Warsaw", "country": { "displayName": "Poland" } } } },
              { "id": 502, "type": "Festival", "status": "cancelled", "displayName": "Mazowsze Fest (x)",
                "series": { "displayName": "Mazowsze Fest" }, "start": { "date": "%s" }, "performance": [],
                "venue": { "displayName": "Błonia", "metroArea": { "displayName": "Warsaw", "country": { "displayName": "Poland" } } } },
              { "id": 503, "type": "Concert", "status": "ok", "displayName": "Kult in Berlin",
                "start": { "date": "%s" }, "performance": [ { "artist": { "displayName": "Kult" } } ],
                "venue": { "displayName": "SO36", "metroArea": { "displayName": "Berlin", "country": { "displayName": "Germany" } } } }
            ] } } }
            """.formatted(dzien, dzien.plusDays(2), dzien.plusDays(4))));
        SecondaryEventImportService.Result r = wtorne().runSongkick();
        em.flush();
        em.clear();
        assertThat(r.seen()).isEqualTo(2);
        MusicEvent po = events.findById(kult.getId()).orElseThrow();
        assertThat(po.getStartTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(po.getLatitude()).isEqualTo(52.2425);
        MusicEvent fest = wydarzenie("songkick:502");
        assertThat(fest.getName()).isEqualTo("Mazowsze Fest");
        assertThat(fest.getStatus()).isEqualTo(com.musicclubapp.entity.EventStatus.CANCELLED);
        assertThat(fest.getSource()).isEqualTo(EventSource.SONGKICK);
        assertThat(events.count()).isEqualTo(2);
        assertThat(server.requests().stream().filter(u -> u.contains("location=geo:52.2297,21.0122")).count())
            .as("jedna strona wystarczyla").isEqualTo(1);
        List<EventSourceEntry> wpisy = sources.findAll();
        assertThat(wpisy).extracting(EventSourceEntry::getSource).containsOnly(EventSource.SONGKICK);
    }
}
