package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.PostRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/** Import wydarzen z Ticketmastera na prawdziwej bazie. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Import wydarzen - dopisywanie, uaktualnianie, usuwanie")
class EventImportServiceTest {

    /** 28 wrzesnia, poludnie w Polsce. Polnoc polska to 22:00 UTC dnia poprzedniego. */
    static final Instant TERAZ = Instant.parse("2026-09-28T10:00:00Z");
    static final String PIERWSZE_OKNO = "startDateTime=2026-09-27T22:00:00Z";

    static final String PUSTO =
        "{\"page\":{\"size\":200,\"totalElements\":0,\"totalPages\":0,\"number\":0}}";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MusicEventRepository repository;
    @Autowired private EventParticipationRepository participationRepository;
    @Autowired private PerformerTagService performerTagService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;

    private TestHttpServer server;
    private TicketmasterClient ticketmaster;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        ticketmaster = new TicketmasterClient("klucz", server.url() + "/discovery/v2", 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** Co ile powtarza sie udany import - jak w application.properties. */
    static final long SZESC_GODZIN = Duration.ofHours(6).toMillis();

    private EventImportService importer(Instant now) {
        return WydarzeniaTestowe.importer(ticketmaster, repository, participationRepository, performerTagService,
            userRepository, transactionManager, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Zegar przestawiany recznie - do sprawdzenia, kiedy import rusza sam. */
    static final class Zegar extends Clock {

        private Instant teraz;

        Zegar(Instant teraz) {
            this.teraz = teraz;
        }

        void przesun(Duration ile) {
            teraz = teraz.plus(ile);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(teraz, zone);
        }

        @Override
        public Instant instant() {
            return teraz;
        }
    }

    @Test
    @DisplayName("po nieudanym imporcie kolejna proba za kwadrans, a nie za 6 godzin")
    void retriesSoonAfterFailure() throws IOException {
        Zegar zegar = new Zegar(TERAZ);
        EventImportService importer = WydarzeniaTestowe.importer(ticketmaster, repository, participationRepository, performerTagService,
            userRepository, transactionManager, zegar);

        server.odpowiadaj(TicketmasterClientTest.SCIEZKA,
            query -> new TestHttpServer.Odpowiedz(503, "{}"));
        importer.scheduledImport();
        assertThat(importer.lastRun().success()).isFalse();

        // Kwadrans pozniej siec wraca - i import rusza przy najblizszym sprawdzeniu
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());
        zegar.przesun(Duration.ofMinutes(15));
        importer.scheduledImport();
        assertThat(importer.lastRun().success()).isTrue();
        assertThat(repository.count()).isEqualTo(10);
        int zapytan = server.requests().size();

        // Po udanym nie ma po co pytac co kwadrans
        zegar.przesun(Duration.ofMinutes(15));
        importer.scheduledImport();
        assertThat(server.requests()).hasSize(zapytan);

        // ... ale po 6 godzinach juz tak
        zegar.przesun(Duration.ofHours(6));
        importer.scheduledImport();
        assertThat(server.requests().size()).isGreaterThan(zapytan);
    }

    /** Pierwszy okres dostaje podana odpowiedz, wszystkie pozostale - pusta. */
    private void ticketmasterOddaje(String pierwszeOkno) {
        server.odpowiadaj(TicketmasterClientTest.SCIEZKA, query -> TestHttpServer.Odpowiedz.ok(
            query.contains(PIERWSZE_OKNO) ? pierwszeOkno : PUSTO));
    }

    /** Odpowiedz z Polski bez wskazanych wydarzen. */
    static String bez(String json, String... ids) throws IOException {
        ObjectNode root = (ObjectNode) JSON.readTree(json);
        ArrayNode events = (ArrayNode) root.path("_embedded").path("events");
        for (Iterator<JsonNode> it = events.elements(); it.hasNext(); ) {
            String id = it.next().path("id").asText();
            for (String doUsuniecia : ids) {
                if (doUsuniecia.equals(id)) {
                    it.remove();
                }
            }
        }
        return JSON.writeValueAsString(root);
    }

    /** Odpowiedz z Polski ze zmieniona nazwa jednego wydarzenia. */
    static String zmien(String json, String id, String nowaNazwa) throws IOException {
        ObjectNode root = (ObjectNode) JSON.readTree(json);
        for (JsonNode event : root.path("_embedded").path("events")) {
            if (id.equals(event.path("id").asText())) {
                ((ObjectNode) event).put("name", nowaNazwa);
            }
        }
        return JSON.writeValueAsString(root);
    }

    private MusicEvent wBazie(String externalId) {
        entityManager.flush();
        entityManager.clear();
        return repository.findByExternalIdIn(java.util.List.of(externalId)).stream().findFirst().orElse(null);
    }

    @Test
    @DisplayName("pierwszy import zapisuje wszystko poza testowymi i tymi bez daty")
    void firstImport() throws IOException {
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());

        EventImportService.ImportStatus wynik = importer(TERAZ).runImport();

        assertThat(wynik.success()).isTrue();
        assertThat(wynik.events()).isEqualTo(10);
        assertThat(repository.count()).isEqualTo(10);

        MusicEvent mrozu = wBazie("vvG1zZ9KSMroz");
        assertThat(mrozu.getCity()).isEqualTo("Łódź");
        assertThat(mrozu.getCityKey()).isEqualTo("lodz");
        assertThat(mrozu.getStartTime()).isNull();
    }

    @Test
    @DisplayName("przechodzi przez caly rok - miesiac po miesiacu")
    void coversTwelveMonths() throws IOException {
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());

        importer(TERAZ).runImport();

        // Od dzisiejszej polnocy do konca wrzesnia, potem pazdziernik ... wrzesien nastepnego roku
        assertThat(server.requests()).hasSize(13);
        assertThat(server.requests().get(0))
            .contains(PIERWSZE_OKNO)
            .contains("endDateTime=2026-09-30T22:00:00Z");
        // Pazdziernik konczy sie juz w czasie zimowym - polnoc to 23:00 UTC
        assertThat(server.requests().get(1)).contains("endDateTime=2026-10-31T23:00:00Z");
        assertThat(server.requests().get(12)).contains("endDateTime=2027-09-27T22:00:00Z");
    }

    @Test
    @DisplayName("drugi import niczego nie dubluje - ani wydarzen, ani skladu")
    void secondImportDoesNotDuplicate() throws IOException {
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());

        importer(TERAZ).runImport();
        importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        assertThat(repository.count()).isEqualTo(10);
        assertThat(wBazie("vvG1zZ9KSAmity").getPerformers()).hasSize(4);
    }

    @Test
    @DisplayName("zmiany u Ticketmastera trafiaja do nas: nazwa, status, sklad")
    void updatesKnownEvents() throws IOException {
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());
        importer(TERAZ).runImport();

        String zmienione = zmien(TicketmasterClientTest.odpowiedzZPolski(), "vvG1zZ9KSStra",
                "Strachy na Lachy - koncert akustyczny")
            // Nazwa wykonawcy - jedyne miejsce w pliku, gdzie "Orthodox" stoi w cudzyslowie w calosci
            .replace("\"Orthodox\"", "\"Orthodox Live\"");
        ticketmasterOddaje(zmienione);
        importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        assertThat(wBazie("vvG1zZ9KSStra").getName()).isEqualTo("Strachy na Lachy - koncert akustyczny");
        assertThat(wBazie("vvG1zZ9KSAmity").getPerformers()).extracting(EventPerformer::getName)
            .containsExactly("The Amity Affliction", "Silent Planet", "Varials", "Orthodox Live");
    }

    @Test
    @DisplayName("czego Ticketmaster juz nie ma, to znika po pelnym imporcie")
    void removesVanishedEvents() throws IOException {
        String wszystko = TicketmasterClientTest.odpowiedzZPolski();
        ticketmasterOddaje(wszystko);
        importer(TERAZ).runImport();

        ticketmasterOddaje(bez(wszystko, "vvG1zZ9KSNach"));
        EventImportService.ImportStatus wynik = importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        assertThat(wynik.removed()).isEqualTo(1);
        assertThat(wBazie("vvG1zZ9KSNach")).isNull();
        assertThat(repository.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("przerwany import niczego nie usuwa")
    void interruptedImportRemovesNothing() throws IOException {
        String wszystko = TicketmasterClientTest.odpowiedzZPolski();
        ticketmasterOddaje(wszystko);
        importer(TERAZ).runImport();

        /* Pierwszy okres przychodzi bez jednego koncertu, a drugi konczy sie bledem. */
        String okrojone = bez(wszystko, "vvG1zZ9KSNach");
        server.odpowiadaj(TicketmasterClientTest.SCIEZKA, query -> query.contains(PIERWSZE_OKNO)
            ? TestHttpServer.Odpowiedz.ok(okrojone)
            : new TestHttpServer.Odpowiedz(429, "{\"fault\":{\"faultstring\":\"Rate limit quota violation\"}}"));

        EventImportService.ImportStatus wynik = importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        assertThat(wynik.success()).isFalse();
        assertThat(wynik.error()).contains("429");
        assertThat(wBazie("vvG1zZ9KSNach")).isNotNull();
        assertThat(repository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("gdy Ticketmaster nagle oddaje mniej niz polowe - nic nie usuwamy")
    void suspiciouslySmallImportRemovesNothing() throws IOException {
        String wszystko = TicketmasterClientTest.odpowiedzZPolski();
        ticketmasterOddaje(wszystko);
        importer(TERAZ).runImport();

        // Zostaja 3 z 10
        ticketmasterOddaje(bez(wszystko, "vvG1zZ9KSAmity", "vvG1zZ9KSVunda", "Z698xZbpZ17Can1",
            "Z698xZbpZ17Can2", "Z698xZbpZ17Can3", "vvG1zZ9KSGlob", "vvG1zZ9KSNach"));
        EventImportService.ImportStatus wynik = importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        assertThat(wynik.success()).isTrue();
        assertThat(wynik.removed()).isZero();
        assertThat(repository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("po miesiacu od wydarzenia usuwamy je z bazy; swiezo minione zostaje")
    void removesOldEvents() throws IOException {
        repository.save(stare("dawno", LocalDate.of(2026, 8, 1)));
        repository.save(stare("niedawno", LocalDate.of(2026, 9, 20)));
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());

        importer(TERAZ).runImport();

        assertThat(wBazie("dawno")).isNull();
        assertThat(wBazie("niedawno")).isNotNull();
    }

    @Test
    @DisplayName("ponad 1000 wynikow w miesiacu: okres dzielony na polowy")
    void splitsCrowdedWindow() {
        String tloczno = "{\"page\":{\"size\":200,\"totalElements\":1500,\"totalPages\":8,\"number\":0}}";
        server.odpowiadaj(TicketmasterClientTest.SCIEZKA, query -> TestHttpServer.Odpowiedz.ok(
            query.contains("startDateTime=2026-09-30T22:00:00Z") && query.contains("endDateTime=2026-10-31T23:00:00Z")
                ? tloczno
                : PUSTO));

        importer(TERAZ).runImport();

        // 31 dni i godzina (zmiana czasu) na pol: 15 dni, 12 godzin i 30 minut
        assertThat(server.requests())
            .anyMatch(r -> r.contains("startDateTime=2026-09-30T22:00:00Z") && r.contains("endDateTime=2026-10-16T10:30:00Z"))
            .anyMatch(r -> r.contains("startDateTime=2026-10-16T10:30:00Z") && r.contains("endDateTime=2026-10-31T23:00:00Z"));
    }

    @Test
    @DisplayName("kolejne strony tego samego okresu")
    void followsPages() throws IOException {
        ObjectNode pierwsza = (ObjectNode) JSON.readTree(TicketmasterClientTest.odpowiedzZPolski());
        ((ObjectNode) pierwsza.path("page")).put("totalPages", 2).put("totalElements", 13);

        ObjectNode druga = pierwsza.deepCopy();
        ArrayNode events = (ArrayNode) druga.path("_embedded").path("events");
        ObjectNode jedno = (ObjectNode) events.get(0).deepCopy();
        jedno.put("id", "druga-strona").put("name", "Koncert z drugiej strony");
        events.removeAll();
        events.add(jedno);
        ((ObjectNode) druga.path("page")).put("number", 1);

        String p1 = pierwsza.toString();
        String p2 = druga.toString();
        server.odpowiadaj(TicketmasterClientTest.SCIEZKA, query -> TestHttpServer.Odpowiedz.ok(
            !query.contains(PIERWSZE_OKNO) ? PUSTO : query.contains("page=1") ? p2 : p1));

        importer(TERAZ).runImport();

        assertThat(wBazie("druga-strona")).isNotNull();
    }

    @Test
    @DisplayName("wycofane z zapisanymi nie znika - chowa sie z listy, a zapis zostaje")
    void vanishedWithParticipantsIsWithdrawn() throws IOException {
        String wszystko = TicketmasterClientTest.odpowiedzZPolski();
        ticketmasterOddaje(wszystko);
        importer(TERAZ).runImport();
        User ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        participationRepository.save(new EventParticipation(wBazie("vvG1zZ9KSNach"), ala,
            ParticipationStatus.GOING, false, LocalDateTime.of(2026, 9, 28, 12, 0)));
        entityManager.flush();

        ticketmasterOddaje(bez(wszystko, "vvG1zZ9KSNach", "vvG1zZ9KSGlob"));
        EventImportService.ImportStatus wynik = importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        // Globus nikogo nie mial - zniknal. Nachtmahr mial zapis - zostal jako wycofany.
        assertThat(wynik.removed()).isEqualTo(1);
        assertThat(wBazie("vvG1zZ9KSGlob")).isNull();
        assertThat(wBazie("vvG1zZ9KSNach").isWithdrawn()).isTrue();
        assertThat(participationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("wycofane z postem pod spodem tez nie znika - odnosnik z posta ma prowadzic do wyjasnienia")
    void vanishedWithPostIsWithdrawn() throws IOException {
        String wszystko = TicketmasterClientTest.odpowiedzZPolski();
        ticketmasterOddaje(wszystko);
        importer(TERAZ).runImport();
        User ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        Post post = new Post(ala, "kto idzie?");
        post.setEvent(wBazie("vvG1zZ9KSNach"));
        postRepository.save(post);
        entityManager.flush();

        ticketmasterOddaje(bez(wszystko, "vvG1zZ9KSNach", "vvG1zZ9KSGlob"));
        EventImportService.ImportStatus wynik = importer(TERAZ.plus(Duration.ofHours(6))).runImport();

        assertThat(wynik.removed()).isEqualTo(1);
        assertThat(wBazie("vvG1zZ9KSGlob")).isNull();
        assertThat(wBazie("vvG1zZ9KSNach").isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("wycofane wraca, gdy Ticketmaster znow je pokazuje")
    void withdrawnComesBack() throws IOException {
        String wszystko = TicketmasterClientTest.odpowiedzZPolski();
        ticketmasterOddaje(wszystko);
        importer(TERAZ).runImport();
        User ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        participationRepository.save(new EventParticipation(wBazie("vvG1zZ9KSNach"), ala,
            ParticipationStatus.GOING, false, LocalDateTime.of(2026, 9, 28, 12, 0)));
        entityManager.flush();

        ticketmasterOddaje(bez(wszystko, "vvG1zZ9KSNach"));
        importer(TERAZ.plus(Duration.ofHours(6))).runImport();
        ticketmasterOddaje(wszystko);
        // +7 h, a nie +12: 10:00 UTC + 12 h to juz polnoc w Polsce, czyli nastepny
        // dzien - a udawany Ticketmaster odpowiada tylko dla okresu od 28 wrzesnia
        importer(TERAZ.plus(Duration.ofHours(7))).runImport();

        assertThat(wBazie("vvG1zZ9KSNach").isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("dawno minione znika razem z zapisami")
    void oldEventTakesParticipationsAlong() throws IOException {
        MusicEvent dawne = repository.save(stare("dawno", LocalDate.of(2026, 8, 1)));
        User ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        participationRepository.save(new EventParticipation(dawne, ala,
            ParticipationStatus.GOING, false, LocalDateTime.of(2026, 7, 28, 12, 0)));
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());

        importer(TERAZ).runImport();

        assertThat(wBazie("dawno")).isNull();
        assertThat(participationRepository.count()).isZero();
    }

    @Test
    @DisplayName("dawno minione znika, ale posty pod nim zostaja jako zwykle wpisy")
    void oldEventLeavesPosts() throws IOException {
        MusicEvent dawne = repository.save(stare("dawno", LocalDate.of(2026, 8, 1)));
        User ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        Post post = new Post(ala, "bylo super");
        post.setEvent(dawne);
        Long id = postRepository.save(post).getId();
        entityManager.flush();
        ticketmasterOddaje(TicketmasterClientTest.odpowiedzZPolski());

        importer(TERAZ).runImport();
        entityManager.flush();
        entityManager.clear();

        assertThat(wBazie("dawno")).isNull();
        Post poImporcie = postRepository.findById(id).orElseThrow();
        assertThat(poImporcie.getContent()).isEqualTo("bylo super");
        assertThat(poImporcie.getEvent()).isNull();
    }

    @Test
    @DisplayName("miasto w jednej postaci - filtr ma widziec jedno miasto, nie trzy")
    void cityKeys() {
        assertThat(EventImportService.cityKey("Łódź ")).isEqualTo("lodz");
        assertThat(EventImportService.cityKey("Lodz")).isEqualTo("lodz");
        assertThat(EventImportService.cityKey("Kraków")).isEqualTo("krakow");
        assertThat(EventImportService.cityKey("Krakow")).isEqualTo("krakow");
        assertThat(EventImportService.cityKey("Warszawa")).isEqualTo("warsaw");
        assertThat(EventImportService.cityKey("Bielsko-Biała")).isEqualTo("bielsko-biala");
        assertThat(EventImportService.cityKey("  ")).isNull();
    }

    @Test
    @DisplayName("seria to ta sama nazwa w tym samym miejscu - wielkosc liter i spacje bez znaczenia")
    void seriesKeys() {
        assertThat(EventImportService.seriesKey("Koncert  przy świecach", "V1", "Sala"))
            .isEqualTo(EventImportService.seriesKey("koncert przy Świecach", "V1", "Sala"));
        assertThat(EventImportService.seriesKey("Koncert przy świecach", "V1", "Sala"))
            .isNotEqualTo(EventImportService.seriesKey("Koncert przy świecach", "V2", "Sala"));
    }

    static MusicEvent stare(String id, LocalDate date) {
        MusicEvent event = new MusicEvent(id);
        event.describe("Minione " + id, null, null, null, null);
        event.schedule(date, null);
        event.place(null, "Gdzies", "Warsaw", "warsaw", null, null, null);
        event.inCountry("PL");
        event.groupAs(EventImportService.seriesKey("Minione " + id, null, "Gdzies"));
        event.markSeen(LocalDateTime.of(2026, 7, 1, 0, 0));
        return event;
    }
}
