package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.MeetingAttendeeRepository;
import com.musicclubapp.repository.MeetingRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spotkania z czatu (rozmowy i klan) przez prawdziwe API: zakladanie z walidacja, odpowiedzi, odwolanie,
 * przypomnienia (zegar przestawiany w tescie), usuniecie razem z wiadomoscia, odswiezanie czatu, eksport i sprzatanie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Spotkania z czatu")
class MeetingFlowTest {

    /** Zegar, ktory idzie z prawdziwym, dopoki test go nie przesunie. */
    static final class Zegar extends Clock {
        private Duration przesuniecie = Duration.ZERO;

        void przesun(Duration ile) {
            przesuniecie = przesuniecie.plus(ile);
        }

        void zeruj() {
            przesuniecie = Duration.ZERO;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(przesuniecie);
        }
    }

    @TestConfiguration
    static class Zegary {
        @Bean
        @Primary
        Zegar zegarTestowy() {
            return new Zegar();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private MeetingRepository meetings;
    @Autowired private MeetingAttendeeRepository attendees;
    @Autowired private NotificationRepository notifications;
    @Autowired private ClanInvitationRepository invitations;
    @Autowired private ReportRepository reports;
    @Autowired private MeetingReminderService reminders;
    @Autowired private DataExportService export;
    @Autowired private AccountDeletionService deletion;
    @Autowired private PasswordEncoder encoder;
    @Autowired private Zegar zegar;
    @Autowired private EntityManager em;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockBean private PushService push;

    @BeforeEach
    void setUp() {
        zegar.zeruj();
        User ala = users.save(new User("sp_ala", "sp_ala@example.com", encoder.encode("haslo123")));
        User bob = users.save(new User("sp_bob", "sp_bob@example.com", encoder.encode("haslo123")));
        users.save(new User("sp_cyd", "sp_cyd@example.com", "x"));
        ala.addFriend(bob);
        users.save(ala);
        em.flush();
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions zapytaj(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String metoda, String kto, String adres, Object tresc) throws Exception {
        var b = switch (metoda) {
            case "DELETE" -> delete(adres);
            case "PUT" -> put(adres);
            default -> post(adres);
        };
        b.with(user(kto)).with(csrf()).header("Accept-Language", "pl");
        if (tresc != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc));
        }
        return mvc.perform(b);
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Instant teraz() {
        return zegar.instant().truncatedTo(ChronoUnit.MINUTES);
    }

    /** Spotkanie za {@code zaMinut} minut, godzina dlugosci, z przypomnieniem. */
    private Map<String, Object> spotkanie(String miejsce, long zaMinut, int przypomnienie) {
        Map<String, Object> m = new HashMap<>();
        m.put("place", miejsce);
        m.put("note", "bilety mam ja");
        m.put("latitude", 52.2297);
        m.put("longitude", 21.0122);
        m.put("startsAt", teraz().plus(Duration.ofMinutes(zaMinut)).toString());
        m.put("endsAt", teraz().plus(Duration.ofMinutes(zaMinut + 60)).toString());
        m.put("remindMinutes", przypomnienie);
        return m;
    }

    private JsonNode wyslijSpotkanie(String kto, String doKogo, Map<String, Object> cialo) throws Exception {
        JsonNode m = tresc(wyslij("POST", kto, "/api/messages/with/" + doKogo + "/meeting", cialo)
            .andExpect(status().isCreated()));
        em.flush();
        return m;
    }

    private JsonNode odpowiedz(String kto, long spotkanie, String status) throws Exception {
        Map<String, Object> cialo = new HashMap<>();
        cialo.put("status", status);
        JsonNode s = tresc(wyslij("PUT", kto, "/api/meetings/" + spotkanie + "/rsvp", cialo).andExpect(status().isOk()));
        em.flush();
        return s;
    }

    private List<String> loginy(JsonNode tablica) {
        List<String> wynik = new ArrayList<>();
        tablica.forEach(n -> wynik.add(n.asText()));
        return wynik;
    }

    private long powiadomienia(String kto, NotificationType typ) {
        return notifications.findAll().stream()
            .filter(n -> n.getRecipient().getUsername().equals(kto) && n.getType() == typ).count();
    }

    private String blad(ResultActions r) throws Exception {
        return tresc(r.andExpect(status().isConflict())).get("message").asText();
    }

    /* ---------------------------- rozmowy ---------------------------- */

    @Test
    @DisplayName("rozmowa: spotkanie to wiadomosc z miejscem i czasem; zakladajacy od razu 'bede', druga strona bez odpowiedzi")
    void createInConversation() throws Exception {
        JsonNode m = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("  Pod Progresja  ", 120, 30));
        assertThat(m.get("content").isNull()).isTrue();
        JsonNode s = m.get("meeting");
        assertThat(s.get("place").asText()).isEqualTo("Pod Progresja");
        assertThat(s.get("note").asText()).isEqualTo("bilety mam ja");
        assertThat(s.get("latitude").asDouble()).isEqualTo(52.2297);
        assertThat(s.get("remindMinutes").asInt()).isEqualTo(30);
        assertThat(s.get("mine").asBoolean()).isTrue();
        assertThat(s.get("myStatus").asText()).isEqualTo("GOING");
        assertThat(s.get("goingCount").asInt()).isEqualTo(1);
        assertThat(loginy(s.get("going"))).containsExactly("sp_ala");
        assertThat(s.get("canRespond").asBoolean()).isTrue();
        assertThat(Instant.parse(s.get("startsAt").asText())).isEqualTo(teraz().plus(Duration.ofMinutes(120)));

        JsonNode u = tresc(zapytaj("sp_bob", "/api/messages/with/sp_ala").andExpect(status().isOk())).get("content").get(0);
        JsonNode jego = u.get("meeting");
        assertThat(jego.get("mine").asBoolean()).isFalse();
        assertThat(jego.get("myStatus").isNull()).isTrue();
        assertThat(jego.get("canRespond").asBoolean()).isTrue();

        // na liscie rozmow podglad ostatniej wiadomosci niesie spotkanie
        JsonNode rozmowy = tresc(zapytaj("sp_bob", "/api/messages/conversations").andExpect(status().isOk()));
        assertThat(rozmowy.get(0).get("lastMessage").get("meeting").get("place").asText()).isEqualTo("Pod Progresja");

        // obcy nie zobaczy spotkania (404, jakby go nie bylo) i nie wysle go do kogos, kto nie jest znajomym
        long id = s.get("id").asLong();
        zapytaj("sp_cyd", "/api/meetings/" + id).andExpect(status().isNotFound());
        wyslij("PUT", "sp_cyd", "/api/meetings/" + id + "/rsvp", Map.of("status", "GOING")).andExpect(status().isNotFound());
        wyslij("POST", "sp_cyd", "/api/messages/with/sp_ala/meeting", spotkanie("X", 60, 0)).andExpect(status().isConflict());
        zapytaj("sp_bob", "/api/meetings/" + id).andExpect(status().isOk());
    }

    @Test
    @DisplayName("walidacja: czas (przeszlosc, 60 dni, koniec, doba), punkt, przypomnienie, miejsce, notatka, zakaz pisania")
    void validation() throws Exception {
        String adres = "/api/messages/with/sp_bob/meeting";
        Map<String, Object> s = spotkanie("Klub", 60, 15);

        s.put("startsAt", teraz().minus(Duration.ofMinutes(10)).toString());
        assertThat(blad(wyslij("POST", "sp_ala", adres, s))).contains("przeszłości");
        // piec minut w przeszlosc wolno (zegar telefonu, chwila na wyslanie)
        s.put("startsAt", teraz().minus(Duration.ofMinutes(4)).toString());
        wyslij("POST", "sp_ala", adres, s).andExpect(status().isCreated());

        s = spotkanie("Klub", 61 * 24 * 60, 15);
        assertThat(blad(wyslij("POST", "sp_ala", adres, s))).contains("60 dni");

        s = spotkanie("Klub", 60, 15);
        s.put("endsAt", s.get("startsAt"));
        assertThat(blad(wyslij("POST", "sp_ala", adres, s))).contains("później");
        s.put("endsAt", teraz().plus(Duration.ofMinutes(60 + 24 * 60 + 1)).toString());
        assertThat(blad(wyslij("POST", "sp_ala", adres, s))).contains("dobę");
        // dokladnie doba jest w porzadku
        s.put("endsAt", teraz().plus(Duration.ofMinutes(60 + 24 * 60)).toString());
        wyslij("POST", "sp_ala", adres, s).andExpect(status().isCreated());

        s = spotkanie("Klub", 60, 45);
        assertThat(blad(wyslij("POST", "sp_ala", adres, s))).contains("przypomnienie");
        s = spotkanie("Klub", 60, 15);
        s.put("longitude", null);
        assertThat(blad(wyslij("POST", "sp_ala", adres, s))).contains("współrzędne");
        s = spotkanie("Klub", 60, 15);
        s.put("latitude", 95.0);
        wyslij("POST", "sp_ala", adres, s).andExpect(status().isUnprocessableEntity());

        // bez punktu na mapie tez mozna
        s = spotkanie("Klub", 60, 0);
        s.put("latitude", null);
        s.put("longitude", null);
        assertThat(wyslijSpotkanie("sp_ala", "sp_bob", s).get("meeting").get("latitude").isNull()).isTrue();

        s = spotkanie("   ", 60, 15);
        wyslij("POST", "sp_ala", adres, s).andExpect(status().isUnprocessableEntity());
        s = spotkanie("x".repeat(101), 60, 15);
        wyslij("POST", "sp_ala", adres, s).andExpect(status().isUnprocessableEntity());
        s = spotkanie("Klub", 60, 15);
        s.put("note", "n".repeat(201));
        wyslij("POST", "sp_ala", adres, s).andExpect(status().isUnprocessableEntity());
        s.put("note", "   ");
        assertThat(wyslijSpotkanie("sp_ala", "sp_bob", s).get("meeting").get("note").isNull()).isTrue();

        User ala = users.findByUsername("sp_ala").orElseThrow();
        ala.setBannedUntil(com.musicclubapp.entity.BanKind.MESSAGING, LocalDateTime.now().plusDays(1));
        users.save(ala);
        em.flush();
        wyslij("POST", "sp_ala", adres, spotkanie("Klub", 60, 15)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("limit: najwyzej 20 nadchodzacych spotkan na osobe; odwolane i zakonczone sie nie licza")
    void limit() throws Exception {
        long pierwsze = 0;
        for (int i = 0; i < MeetingService.MAX_OPEN; i++) {
            long id = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Klub " + i, 60 + i, 0)).get("meeting").get("id").asLong();
            pierwsze = i == 0 ? id : pierwsze;
        }
        assertThat(blad(wyslij("POST", "sp_ala", "/api/messages/with/sp_bob/meeting", spotkanie("Klub", 60, 0))))
            .contains("20");
        // druga osoba ma wlasny limit
        wyslijSpotkanie("sp_bob", "sp_ala", spotkanie("Klub", 60, 0));
        wyslij("POST", "sp_ala", "/api/meetings/" + pierwsze + "/cancel", null).andExpect(status().isOk());
        em.flush();
        wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Klub po odwolaniu", 60, 0));
    }

    @Test
    @DisplayName("odpowiedzi: bede / nie dam rady / cofniecie; liczniki i lista; odswiezanie rozmowy oddaje zmienione spotkanie")
    void rsvpAndSync() throws Exception {
        long id = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Hala", 120, 30)).get("meeting").get("id").asLong();
        // spotkanie wyslane godzine temu - bez nowych odpowiedzi nie ma czego odswiezac
        jdbc.update("UPDATE meetings SET updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(1))), id);
        JsonNode przed = tresc(zapytaj("sp_ala", "/api/messages/with/sp_bob/sync?after=0").andExpect(status().isOk()));
        String czas = przed.get("serverTime").asText();
        assertThat(przed.get("meetings")).isEmpty();
        assertThat(tresc(zapytaj("sp_ala", "/api/messages/with/sp_bob/sync?after=0&changedSince=" + czas)).get("meetings"))
            .as("stare spotkanie bez zmian").isEmpty();

        JsonNode s = odpowiedz("sp_bob", id, "GOING");
        assertThat(s.get("myStatus").asText()).isEqualTo("GOING");
        assertThat(s.get("goingCount").asInt()).isEqualTo(2);
        assertThat(loginy(s.get("going"))).containsExactly("sp_ala", "sp_bob");

        JsonNode po = tresc(zapytaj("sp_ala", "/api/messages/with/sp_bob/sync?after=0&changedSince=" + czas)
            .andExpect(status().isOk()));
        JsonNode zmienione = po.get("meetings").get(0);
        assertThat(zmienione.get("id").asLong()).isEqualTo(id);
        assertThat(zmienione.get("goingCount").asInt()).isEqualTo(2);
        assertThat(zmienione.get("myStatus").asText()).isEqualTo("GOING");
        // bez czasu nie ma zmian
        assertThat(tresc(zapytaj("sp_ala", "/api/messages/with/sp_bob/sync?after=0")).get("meetings")).isEmpty();

        s = odpowiedz("sp_bob", id, "NOT_GOING");
        assertThat(s.get("goingCount").asInt()).isEqualTo(1);
        assertThat(s.get("notGoingCount").asInt()).isEqualTo(1);
        s = odpowiedz("sp_bob", id, null);
        assertThat(s.get("myStatus").isNull()).isTrue();
        assertThat(s.get("notGoingCount").asInt()).isZero();
        assertThat(attendees.findAll()).hasSize(1);

        // po zerwaniu znajomosci spotkanie widac (historia zostaje), ale odpowiadac sie nie da
        User ala = users.findByUsername("sp_ala").orElseThrow();
        User bob = users.findByUsername("sp_bob").orElseThrow();
        ala.removeFriend(bob);
        users.save(ala);
        em.flush();
        assertThat(tresc(zapytaj("sp_bob", "/api/meetings/" + id).andExpect(status().isOk())).get("canRespond").asBoolean())
            .isFalse();
        wyslij("PUT", "sp_bob", "/api/meetings/" + id + "/rsvp", Map.of("status", "GOING")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("odwolanie: tylko zakladajacy, potwierdzeni dostaja powiadomienie z linkiem do rozmowy; potem nic sie nie da")
    void cancel() throws Exception {
        long id = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Hala", 120, 30)).get("meeting").get("id").asLong();
        odpowiedz("sp_bob", id, "GOING");
        assertThat(blad(wyslij("POST", "sp_bob", "/api/meetings/" + id + "/cancel", null))).contains("tylko osoba");
        // przypomnienia juz poszly - po odwolaniu maja zniknac z dzwonkow obu osob
        zegar.przesun(Duration.ofMinutes(91));
        assertThat(reminders.run()).isEqualTo(2);
        em.flush();
        clearInvocations(push);

        JsonNode s = tresc(wyslij("POST", "sp_ala", "/api/meetings/" + id + "/cancel", null).andExpect(status().isOk()));
        em.flush();
        assertThat(s.get("cancelled").asBoolean()).isTrue();
        assertThat(s.get("canRespond").asBoolean()).isFalse();
        assertThat(powiadomienia("sp_bob", NotificationType.MEETING_CANCELLED)).isEqualTo(1);
        assertThat(powiadomienia("sp_ala", NotificationType.MEETING_CANCELLED)).isZero();
        assertThat(powiadomienia("sp_bob", NotificationType.MEETING_REMINDER)).isZero();
        assertThat(powiadomienia("sp_ala", NotificationType.MEETING_REMINDER)).isZero();
        JsonNode dzwonek = tresc(zapytaj("sp_bob", "/api/notifications").andExpect(status().isOk())).get("content").get(0);
        assertThat(dzwonek.get("link").asText()).isEqualTo("/?czat=sp_ala");
        assertThat(dzwonek.get("meetingPlace").asText()).isEqualTo("Hala");
        assertThat(dzwonek.get("actorUsername").asText()).isEqualTo("sp_ala");
        Long bobId = users.findByUsername("sp_bob").orElseThrow().getId();
        verify(push).send(eq(bobId), argThat(m -> m.titleKey().equals("push.meetingCancelled.title")));

        wyslij("PUT", "sp_bob", "/api/meetings/" + id + "/rsvp", Map.of("status", "NOT_GOING")).andExpect(status().isConflict());
        wyslij("POST", "sp_ala", "/api/meetings/" + id + "/cancel", null).andExpect(status().isConflict());
        // odwolane nie przypomina
        zegar.przesun(Duration.ofMinutes(5));
        assertThat(reminders.run()).isZero();
    }

    @Test
    @DisplayName("przypomnienie: raz, o czasie, dla potwierdzonych (takze zakladajacego); 'nie dam rady' zdejmuje je z dzwonka")
    void reminders() throws Exception {
        long id = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Hala", 120, 30)).get("meeting").get("id").asLong();
        odpowiedz("sp_bob", id, "GOING");
        Long alaId = users.findByUsername("sp_ala").orElseThrow().getId();
        Long bobId = users.findByUsername("sp_bob").orElseThrow().getId();
        clearInvocations(push);

        assertThat(reminders.run()).isZero();
        zegar.przesun(Duration.ofMinutes(89));
        assertThat(reminders.run()).isZero();
        zegar.przesun(Duration.ofMinutes(1));
        assertThat(reminders.run()).isEqualTo(2);
        em.flush();
        assertThat(reminders.run()).isZero();

        // 30 min przed startem; 29, gdy przebieg testu przekroczyl granice minuty (minuty liczone w chwili wysylki)
        verify(push).send(eq(bobId), argThat(m -> m.titleKey().equals("push.meeting.minutes")
            && List.of(29L, 30L).contains(m.titleArgs()[0]) && m.url().equals("/?czat=sp_ala") && m.tag().equals("meeting-" + id)));
        verify(push).send(eq(alaId), argThat(m -> m.url().equals("/?czat=sp_bob")));
        JsonNode dzwonek = tresc(zapytaj("sp_bob", "/api/notifications")).get("content").get(0);
        assertThat(dzwonek.get("type").asText()).isEqualTo("MEETING_REMINDER");
        assertThat(dzwonek.get("meetingId").asLong()).isEqualTo(id);
        assertThat(Instant.parse(dzwonek.get("meetingStartsAt").asText())).isAfter(Instant.now());

        // zmiana na "nie dam rady" zdejmuje przypomnienie, powrot na "bede" nie wysyla drugiego
        odpowiedz("sp_bob", id, "NOT_GOING");
        assertThat(powiadomienia("sp_bob", NotificationType.MEETING_REMINDER)).isZero();
        odpowiedz("sp_bob", id, "GOING");
        assertThat(reminders.run()).isZero();
        assertThat(powiadomienia("sp_ala", NotificationType.MEETING_REMINDER)).isEqualTo(1);
    }

    @Test
    @DisplayName("przypomnienie: nie po czasie, nie po zerwaniu znajomosci, nie przy 'bez przypomnienia'; spoznione po starcie serwera")
    void remindersEdgeCases() throws Exception {
        long bez = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Bez", 60, 0)).get("meeting").get("id").asLong();
        long poCzasie = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Po czasie", 30, 15)).get("meeting").get("id").asLong();
        long zerwane = wyslijSpotkanie("sp_bob", "sp_ala", spotkanie("Zerwane", 300, 60)).get("meeting").get("id").asLong();
        odpowiedz("sp_bob", bez, "GOING");
        odpowiedz("sp_bob", poCzasie, "GOING");
        odpowiedz("sp_ala", zerwane, "GOING");

        // serwer stal: o "Po czasie" przypomina dopiero w trakcie (spoznione), po koncu juz nie
        zegar.przesun(Duration.ofMinutes(35));
        assertThat(reminders.run()).isEqualTo(2);
        em.flush();
        clearInvocations(push);

        long pozne = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Pozne", 10, 15)).get("meeting").get("id").asLong();
        // potwierdzenie juz po chwili przypomnienia - osoba wie, ze to za chwile; zadnego przypomnienia
        odpowiedz("sp_bob", pozne, "GOING");
        assertThat(reminders.run()).isZero();

        User ala = users.findByUsername("sp_ala").orElseThrow();
        ala.removeFriend(users.findByUsername("sp_bob").orElseThrow());
        users.save(ala);
        em.flush();
        zegar.przesun(Duration.ofMinutes(250));
        assertThat(reminders.run()).isZero();
        verify(push, never()).send(any(), argThat(m -> m.tag().equals("meeting-" + zerwane)));
    }

    @Test
    @DisplayName("przypomnienie: nie po koncu spotkania (serwer stal dluzej)")
    void noReminderAfterEnd() throws Exception {
        long koniec = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Koniec", 20, 15)).get("meeting").get("id").asLong();
        odpowiedz("sp_bob", koniec, "GOING");
        zegar.przesun(Duration.ofMinutes(100));
        assertThat(reminders.run()).as("po koncu").isZero();
    }

    @Test
    @DisplayName("przypomnienie: 'nie dam rady' go nie dostaje, zakladajaca tak")
    void noReminderForNotGoing() throws Exception {
        long nie = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Nie dam rady", 60, 30)).get("meeting").get("id").asLong();
        odpowiedz("sp_bob", nie, "NOT_GOING");
        zegar.przesun(Duration.ofMinutes(31));
        assertThat(reminders.run()).as("tylko zakladajaca").isEqualTo(1);
        em.flush();
        assertThat(powiadomienia("sp_bob", NotificationType.MEETING_REMINDER)).isZero();
    }

    @Test
    @DisplayName("usuniecie wiadomosci kasuje spotkanie (z odpowiedziami i powiadomieniami); slad bez spotkania")
    void deleteWithMessage() throws Exception {
        JsonNode m = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Hala", 120, 30));
        long id = m.get("meeting").get("id").asLong();
        odpowiedz("sp_bob", id, "GOING");
        zegar.przesun(Duration.ofMinutes(91));
        assertThat(reminders.run()).isEqualTo(2);
        em.flush();

        JsonNode slad = tresc(wyslij("DELETE", "sp_ala", "/api/messages/" + m.get("id").asLong(), null)
            .andExpect(status().isOk()));
        em.flush();
        em.clear();
        assertThat(slad.get("deleted").asBoolean()).isTrue();
        assertThat(slad.get("meeting").isNull()).isTrue();
        assertThat(meetings.findById(id)).isEmpty();
        assertThat(attendees.findAll()).isEmpty();
        assertThat(powiadomienia("sp_bob", NotificationType.MEETING_REMINDER)).isZero();
        zapytaj("sp_bob", "/api/meetings/" + id).andExpect(status().isNotFound());
        JsonNode u = tresc(zapytaj("sp_bob", "/api/messages/with/sp_ala")).get("content").get(0);
        assertThat(u.get("deleted").asBoolean()).isTrue();
        assertThat(u.get("meeting").isNull()).isTrue();
    }

    @Test
    @DisplayName("zgloszenie rozmowy ma spotkanie w dowodzie; rozmowa skasowana przez obie strony zabiera spotkania")
    void reportAndConversationDelete() throws Exception {
        wyslijSpotkanie("sp_bob", "sp_ala", spotkanie("Ciemna brama", 120, 0));
        long zgloszenie = tresc(wyslij("POST", "sp_ala", "/api/reports/on/sp_bob", Map.of("reason", "HARASSMENT",
            "context", "CONVERSATION", "description", "dziwne zaproszenie")).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        em.clear();
        List<String> dowody = new ArrayList<>();
        reports.findById(zgloszenie).orElseThrow().getEvidence().forEach(e -> dowody.add(e.getText()));
        assertThat(dowody).anyMatch(d -> d.startsWith("[SPOTKANIE] Ciemna brama (52.2297, 21.0122), ")
            && d.endsWith(": bilety mam ja"));

        wyslij("DELETE", "sp_ala", "/api/messages/with/sp_bob", null).andExpect(status().isNoContent());
        em.flush();
        assertThat(meetings.count()).isEqualTo(1); // bob dalej widzi rozmowe
        wyslij("DELETE", "sp_bob", "/api/messages/with/sp_ala", null).andExpect(status().isNoContent());
        em.flush();
        assertThat(meetings.count()).isZero();
    }

    @Test
    @DisplayName("eksport ma moje spotkania i odpowiedzi; usuniecie konta zabiera spotkania z jego rozmow")
    void exportAndAccountDeletion() throws Exception {
        long id = wyslijSpotkanie("sp_ala", "sp_bob", spotkanie("Hala", 120, 30)).get("meeting").get("id").asLong();
        wyslijSpotkanie("sp_bob", "sp_ala", spotkanie("Bramka", 240, 0));
        odpowiedz("sp_bob", id, "NOT_GOING");

        @SuppressWarnings("unchecked")
        Map<String, Object> dane = (Map<String, Object>) export.przygotuj("sp_ala", "haslo123").dane().get("meetings");
        JsonNode moje = json.valueToTree(dane);
        assertThat(moje.get("created")).hasSize(1);
        assertThat(moje.get("created").get(0).get("place").asText()).isEqualTo("Hala");
        assertThat(moje.get("created").get(0).get("with").asText()).isEqualTo("sp_bob");
        assertThat(moje.get("created").get(0).get("latitude").asDouble()).isEqualTo(52.2297);
        assertThat(moje.get("responses")).hasSize(1); // tylko moje "bede" na wlasnym
        JsonNode jego = json.valueToTree(export.przygotuj("sp_bob", "haslo123").dane().get("meetings"));
        assertThat(jego.get("responses")).hasSize(2);
        assertThat(jego.get("responses").findValuesAsText("status")).contains("NOT_GOING", "GOING");

        deletion.erase(users.findByUsername("sp_ala").orElseThrow());
        em.flush();
        em.clear();
        assertThat(meetings.count()).isZero();
        assertThat(attendees.count()).isZero();
    }

    /* ---------------------------- klan ---------------------------- */

    private long klanAliIBoba() throws Exception {
        long klan = tresc(wyslij("POST", "sp_ala", "/api/clans", Map.of("name", "Spotkaniowcy", "tag", "SPO"))
            .andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        wyslij("POST", "sp_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "sp_bob")).andExpect(status().isOk());
        em.flush();
        long zaproszenie = invitations.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals("sp_bob")).findFirst().orElseThrow().getId();
        wyslij("POST", "sp_bob", "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
        em.flush();
        return klan;
    }

    @Test
    @DisplayName("klan: spotkanie na czacie, odpowiedzi czlonkow, zmiany przy odswiezaniu, cytat, odejscie zdejmuje odpowiedz")
    void clanMeeting() throws Exception {
        long klan = klanAliIBoba();
        JsonNode m = tresc(wyslij("POST", "sp_ala", "/api/clans/" + klan + "/chat/meeting", spotkanie("Pod sceną", 120, 30))
            .andExpect(status().isCreated()));
        em.flush();
        long id = m.get("meeting").get("id").asLong();
        assertThat(m.get("content").asText()).isEmpty();
        assertThat(m.get("meeting").get("mine").asBoolean()).isTrue();

        JsonNode przed = tresc(zapytaj("sp_bob", "/api/clans/" + klan + "/chat/changes"));
        String czas = przed.get("serverTime").asText();
        JsonNode czat = tresc(zapytaj("sp_bob", "/api/clans/" + klan + "/chat").andExpect(status().isOk()));
        JsonNode jego = czat.get(czat.size() - 1).get("meeting");
        assertThat(jego.get("place").asText()).isEqualTo("Pod sceną");
        assertThat(jego.get("canRespond").asBoolean()).isTrue();
        odpowiedz("sp_bob", id, "GOING");
        JsonNode zmiany = tresc(zapytaj("sp_ala", "/api/clans/" + klan + "/chat/changes?since=" + czas));
        assertThat(zmiany.get("meetings").get(0).get("goingCount").asInt()).isEqualTo(2);

        // trzecia osoba w klanie; kto ja zablokuje, nie widzi jej na spotkaniu (ani w liczniku)
        wyslij("POST", "sp_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "sp_cyd")).andExpect(status().isOk());
        em.flush();
        long zaprCyd = invitations.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals("sp_cyd")).findFirst().orElseThrow().getId();
        wyslij("POST", "sp_cyd", "/api/clans/invitations/" + zaprCyd + "/accept", null).andExpect(status().isOk());
        em.flush();
        odpowiedz("sp_cyd", id, "GOING");
        wyslij("PUT", "sp_bob", "/api/blocks/sp_cyd", null).andExpect(status().is2xxSuccessful());
        em.flush();
        JsonNode uBoba = tresc(zapytaj("sp_bob", "/api/meetings/" + id));
        assertThat(uBoba.get("goingCount").asInt()).isEqualTo(2);
        assertThat(loginy(uBoba.get("going"))).doesNotContain("sp_cyd");
        assertThat(tresc(zapytaj("sp_ala", "/api/meetings/" + id)).get("goingCount").asInt()).isEqualTo(3);
        // i odchodzi z klanu - dalej jest obca
        wyslij("DELETE", "sp_cyd", "/api/clans/" + klan + "/members/me", null).andExpect(status().is2xxSuccessful());
        em.flush();

        // odpowiedz na spotkanie cytuje jego miejsce
        JsonNode odp = tresc(wyslij("POST", "sp_bob", "/api/clans/" + klan + "/chat",
            Map.of("content", "jestem!", "replyTo", m.get("id").asLong())).andExpect(status().isCreated()));
        assertThat(odp.get("replyTo").get("meeting").asBoolean()).isTrue();
        assertThat(odp.get("replyTo").get("excerpt").asText()).isEqualTo("Pod sceną");

        // obcy: 404; wysylac na czat klanu tez nie moze
        zapytaj("sp_cyd", "/api/meetings/" + id).andExpect(status().isNotFound());
        wyslij("PUT", "sp_cyd", "/api/meetings/" + id + "/rsvp", Map.of("status", "GOING")).andExpect(status().isNotFound());
        wyslij("POST", "sp_cyd", "/api/clans/" + klan + "/chat/meeting", spotkanie("X", 60, 0)).andExpect(status().is4xxClientError());

        // przypomnienie klanu prowadzi do czatu klanu
        clearInvocations(push);
        zegar.przesun(Duration.ofMinutes(90));
        assertThat(reminders.run()).isEqualTo(2);
        em.flush();
        Long bobId = users.findByUsername("sp_bob").orElseThrow().getId();
        verify(push).send(eq(bobId), argThat(msg -> msg.url().equals("/klan")));
        zegar.zeruj();

        // odejscie z klanu zdejmuje odpowiedz - nowe spotkanie nie przypomni bylemu czlonkowi
        long drugie = tresc(wyslij("POST", "sp_ala", "/api/clans/" + klan + "/chat/meeting", spotkanie("Drugie", 300, 60))
            .andExpect(status().isCreated())).get("meeting").get("id").asLong();
        em.flush();
        odpowiedz("sp_bob", drugie, "GOING");
        wyslij("DELETE", "sp_bob", "/api/clans/" + klan + "/members/me", null).andExpect(status().is2xxSuccessful());
        em.flush();
        assertThat(attendees.findByMeetingIdAndUserId(drugie, bobId)).isEmpty();
        zapytaj("sp_bob", "/api/meetings/" + drugie).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("klan: usuniecie wiadomosci przez zarzad kasuje spotkanie; rozwiazanie klanu tez")
    void clanDeleteAndDissolve() throws Exception {
        long klan = klanAliIBoba();
        JsonNode m = tresc(wyslij("POST", "sp_bob", "/api/clans/" + klan + "/chat/meeting", spotkanie("Bramka", 120, 30))
            .andExpect(status().isCreated()));
        em.flush();
        long id = m.get("meeting").get("id").asLong();
        wyslij("DELETE", "sp_ala", "/api/clans/" + klan + "/chat/" + m.get("id").asLong(), null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(meetings.findById(id)).isEmpty();

        tresc(wyslij("POST", "sp_ala", "/api/clans/" + klan + "/chat/meeting", spotkanie("Trzecie", 120, 30))
            .andExpect(status().isCreated()));
        em.flush();
        assertThat(meetings.count()).isEqualTo(1);
        wyslij("DELETE", "sp_ala", "/api/clans/" + klan, null).andExpect(status().is2xxSuccessful());
        em.flush();
        em.clear();
        assertThat(meetings.count()).isZero();
    }
}
