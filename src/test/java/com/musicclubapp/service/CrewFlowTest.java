package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Crew;
import com.musicclubapp.entity.CrewMember;
import com.musicclubapp.entity.CrewRole;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.CrewMemberRepository;
import com.musicclubapp.repository.CrewMessageRepository;
import com.musicclubapp.repository.CrewRepository;
import com.musicclubapp.repository.CrewRequestRepository;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MeetingAttendeeRepository;
import com.musicclubapp.repository.MeetingRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.NotificationRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ekipy na koncert przez prawdziwe API: zakladanie, dolaczanie od razu i za zgoda, zarzad zakladajacego, odejscie
 * z przekazaniem, blokady, czat (nieprzeczytane, push, usuwanie, zamkniecie po koncercie), spotkania ekipy, zapis
 * "Biore udzial", eksport, usuniecie konta i to, co znika razem z wydarzeniem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Ekipy na koncert")
class CrewFlowTest {

    @TestConfiguration
    static class Zegary {
        @Bean
        @Primary
        MeetingFlowTest.Zegar zegarTestowy() {
            return new MeetingFlowTest.Zegar();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private MusicEventRepository events;
    @Autowired private EventParticipationRepository participations;
    @Autowired private CrewRepository crews;
    @Autowired private CrewMemberRepository members;
    @Autowired private CrewRequestRepository requests;
    @Autowired private CrewMessageRepository crewMessages;
    @Autowired private MeetingRepository meetings;
    @Autowired private MeetingAttendeeRepository attendees;
    @Autowired private NotificationRepository notifications;
    @Autowired private MeetingService meetingService;
    @Autowired private MeetingReminderService reminders;
    @Autowired private CrewRequestCleanup cleanup;
    @Autowired private LocationService location;
    @Autowired private EventImportService importer;
    @Autowired private DataExportService export;
    @Autowired private AccountDeletionService deletion;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MeetingFlowTest.Zegar zegar;
    @Autowired private EntityManager em;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockBean private PushService push;

    private long koncert;

    @BeforeEach
    void setUp() {
        zegar.zeruj();
        for (String login : List.of("ek_ala", "ek_bob", "ek_cyd", "ek_dan", "ek_ewa")) {
            users.save(new User(login, login + "@example.com", encoder.encode("haslo123")));
        }
        em.flush();
        koncert = wydarzenie("EK-1", 20);
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private long wydarzenie(String id, int zaIleDni) {
        MusicEvent e = WydarzeniaTestowe.wydarzenie(id, "Koncert " + id, importer.today().plusDays(zaIleDni),
            "Warszawa", "Rock", null, "Kult");
        long numer = events.save(e).getId();
        em.flush();
        return numer;
    }

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
        ResultActions r = mvc.perform(b);
        em.flush();
        return r;
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String blad(ResultActions r) throws Exception {
        return tresc(r.andExpect(status().isConflict())).get("message").asText();
    }

    private Map<String, Object> formularz(String tytul, int miejsca, String nabor, String miasto) {
        Map<String, Object> f = new HashMap<>();
        f.put("title", tytul);
        f.put("description", "Jedziemy pociagiem o 16:10");
        f.put("capacity", miejsca);
        f.put("joinPolicy", nabor);
        f.put("departureCity", miasto);
        return f;
    }

    /** Zaklada ekipe i oddaje jej numer. */
    private long zaloz(String kto, long wydarzenie, int miejsca, String nabor) throws Exception {
        JsonNode e = tresc(wyslij("POST", kto, "/api/events/" + wydarzenie + "/crews",
            formularz("Ekipa " + kto, miejsca, nabor, "Warszawa")).andExpect(status().isCreated()));
        return e.get("crew").get("id").asLong();
    }

    private JsonNode dolacz(String kto, long ekipa, String wiadomosc) throws Exception {
        Map<String, Object> cialo = new HashMap<>();
        cialo.put("message", wiadomosc);
        return tresc(wyslij("POST", kto, "/api/crews/" + ekipa + "/join", cialo).andExpect(status().isOk()));
    }

    private JsonNode karta(String kto, long wydarzenie, long ekipa) throws Exception {
        for (JsonNode k : tresc(zapytaj(kto, "/api/events/" + wydarzenie + "/crews").andExpect(status().isOk()))) {
            if (k.get("id").asLong() == ekipa) {
                return k;
            }
        }
        return null;
    }

    private JsonNode kartaWydarzenia(String kto, long wydarzenie) throws Exception {
        for (JsonNode k : tresc(zapytaj(kto, "/api/events?view=UPCOMING&size=50").andExpect(status().isOk())).get("content")) {
            if (k.get("id").asLong() == wydarzenie) {
                return k;
            }
        }
        throw new AssertionError("nie ma wydarzenia " + wydarzenie + " na liscie");
    }

    private JsonNode ekipa(String kto, long ekipa) throws Exception {
        return tresc(zapytaj(kto, "/api/crews/" + ekipa).andExpect(status().isOk()));
    }

    private List<String> loginy(JsonNode osoby) {
        List<String> wynik = new ArrayList<>();
        osoby.forEach(o -> wynik.add(o.get("username").asText()));
        return wynik;
    }

    private long powiadomienia(String kto, NotificationType typ) {
        return notifications.findAll().stream()
            .filter(n -> n.getRecipient().getUsername().equals(kto) && n.getType() == typ).count();
    }

    private Long id(String login) {
        return users.findByUsername(login).orElseThrow().getId();
    }

    private ParticipationStatus zapis(String kto, long wydarzenie) {
        return participations.findMine(wydarzenie, kto).map(p -> p.getStatus()).orElse(null);
    }

    private JsonNode napisz(String kto, long ekipa, String tekst) throws Exception {
        return tresc(wyslij("POST", kto, "/api/crews/" + ekipa + "/chat", Map.of("content", tekst))
            .andExpect(status().isCreated()));
    }

    private Map<String, Object> spotkanie(String miejsce, long zaMinut, int przypomnienie) {
        Instant teraz = zegar.instant().truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> m = new HashMap<>();
        m.put("place", miejsce);
        m.put("latitude", 52.2297);
        m.put("longitude", 21.0122);
        m.put("startsAt", teraz.plus(Duration.ofMinutes(zaMinut)).toString());
        m.put("endsAt", teraz.plus(Duration.ofMinutes(zaMinut + 60)).toString());
        m.put("remindMinutes", przypomnienie);
        return m;
    }

    /* ---------------------------- zakladanie ---------------------------- */

    @Test
    @DisplayName("zalozenie: zakladajacy w ekipie i 'Biore udzial'; miasto z listy; jedna ekipa na koncert; walidacja")
    void create() throws Exception {
        JsonNode e = tresc(wyslij("POST", "ek_ala", "/api/events/" + koncert + "/crews",
            formularz("  Z Warszawy  ", 4, "OPEN", "warszawa")).andExpect(status().isCreated()));
        JsonNode k = e.get("crew");
        assertThat(k.get("title").asText()).isEqualTo("Z Warszawy");
        assertThat(k.get("myState").asText()).isEqualTo("FOUNDER");
        assertThat(k.get("members").asInt()).isEqualTo(1);
        assertThat(k.get("capacity").asInt()).isEqualTo(4);
        assertThat(k.get("departureCity").asText()).as("nazwa z listy miast").isEqualTo("Warszawa");
        assertThat(k.get("canJoin").asBoolean()).isFalse();
        assertThat(e.get("eventId").asLong()).isEqualTo(koncert);
        assertThat(e.get("chatOpen").asBoolean()).isTrue();
        assertThat(loginy(e.get("members"))).containsExactly("ek_ala");
        assertThat(zapis("ek_ala", koncert)).isEqualTo(ParticipationStatus.GOING);

        // ogladajacy z Warszawy: ta sama okolica, moze dolaczyc
        location.update("ek_bob", "Warszawa");
        em.flush();
        long ekipa = k.get("id").asLong();
        JsonNode u = karta("ek_bob", koncert, ekipa);
        assertThat(u.get("near").asInt()).isEqualTo(5);
        assertThat(u.get("distanceKm").asInt()).isZero();
        assertThat(u.get("canJoin").asBoolean()).isTrue();
        assertThat(u.get("myState").isNull()).isTrue();
        assertThat(loginy(u.get("preview"))).containsExactly("ek_ala");
        // bez miasta w profilu - nie wiadomo, jak daleko
        assertThat(karta("ek_cyd", koncert, ekipa).get("distanceKm").isNull()).isTrue();

        // druga ekipa na ten sam koncert - nie
        assertThat(blad(wyslij("POST", "ek_ala", "/api/events/" + koncert + "/crews",
            formularz("Druga", 4, "OPEN", null)))).contains("Jesteś już w ekipie");

        // walidacja: 2-12 osob, nazwa do 60, nabor wymagany
        String adres = "/api/events/" + koncert + "/crews";
        wyslij("POST", "ek_bob", adres, formularz("A", 1, "OPEN", null)).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "ek_bob", adres, formularz("A", 13, "OPEN", null)).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "ek_bob", adres, formularz("x".repeat(61), 4, "OPEN", null)).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "ek_bob", adres, formularz("A", 4, null, null)).andExpect(status().isUnprocessableEntity());
        // dokladnie na granicach - wolno
        long graniczna = tresc(wyslij("POST", "ek_bob", adres, formularz("x".repeat(60), 12, "APPROVAL", " "))
            .andExpect(status().isCreated())).get("crew").get("id").asLong();
        JsonNode g = ekipa("ek_bob", graniczna).get("crew");
        assertThat(g.get("departureCity").isNull()).as("puste miasto = bez miasta").isTrue();
        assertThat(g.get("capacity").asInt()).isEqualTo(12);

        // minione i wycofane - nie
        long minione = wydarzenie("EK-MIN", -1);
        assertThat(blad(wyslij("POST", "ek_cyd", "/api/events/" + minione + "/crews", formularz("A", 4, "OPEN", null))))
            .contains("już się odbyło");
        long wycofane = wydarzenie("EK-WYC", 5);
        events.findById(wycofane).orElseThrow().withdraw(LocalDateTime.now());
        em.flush();
        assertThat(blad(wyslij("POST", "ek_cyd", "/api/events/" + wycofane + "/crews", formularz("A", 4, "OPEN", null))))
            .contains("Ticketmasterze");
        wyslij("POST", "ek_cyd", "/api/events/999999/crews", formularz("A", 4, "OPEN", null))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("limit: najwyzej 10 ekip na nadchodzace koncerty na osobe; miniona sie nie liczy")
    void foundedLimit() throws Exception {
        for (int i = 0; i < CrewService.MAX_FOUNDED; i++) {
            zaloz("ek_ala", wydarzenie("EK-L" + i, 3 + i), 4, "OPEN");
        }
        long jedenaste = wydarzenie("EK-L10", 30);
        assertThat(blad(wyslij("POST", "ek_ala", "/api/events/" + jedenaste + "/crews", formularz("A", 4, "OPEN", null))))
            .contains("10");
        // koncert z pierwsza ekipa juz minal - miejsce sie zwalnia
        zegar.przesun(Duration.ofDays(4));
        zaloz("ek_ala", jedenaste, 4, "OPEN");
    }

    /* ---------------------------- dolaczanie ---------------------------- */

    @Test
    @DisplayName("nabor otwarty: dolaczenie od razu z zapisem; pelna i zamknieta; rezygnacja z koncertu dopiero po odejsciu")
    void joinOpen() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 3, "OPEN");
        clearInvocations(push);

        JsonNode k = dolacz("ek_bob", ekipa, null);
        assertThat(k.get("myState").asText()).isEqualTo("MEMBER");
        assertThat(k.get("members").asInt()).isEqualTo(2);
        assertThat(zapis("ek_bob", koncert)).isEqualTo(ParticipationStatus.GOING);
        assertThat(powiadomienia("ek_ala", NotificationType.CREW_MEMBER_JOINED)).isEqualTo(1);
        verify(push, never()).send(eq(id("ek_ala")), any());
        // karta na liscie wydarzen: ile ekip i moja
        JsonNode uBoba = kartaWydarzenia("ek_bob", koncert);
        assertThat(uBoba.get("crews").asLong()).isEqualTo(1);
        assertThat(uBoba.get("myCrewId").asLong()).isEqualTo(ekipa);
        assertThat(kartaWydarzenia("ek_dan", koncert).get("myCrewId").isNull()).isTrue();
        // dolaczenie zapisuje jako "ide" takze kogos, kto byl tylko zainteresowany
        wyslij("PUT", "ek_cyd", "/api/events/" + koncert + "/participation", Map.of("status", "INTERESTED"))
            .andExpect(status().isOk());
        dolacz("ek_cyd", ekipa, null);
        assertThat(zapis("ek_cyd", koncert)).isEqualTo(ParticipationStatus.GOING);

        // pelna: 3 z 3
        JsonNode u = karta("ek_dan", koncert, ekipa);
        assertThat(u.get("canJoin").asBoolean()).isFalse();
        assertThat(blad(wyslij("POST", "ek_dan", "/api/crews/" + ekipa + "/join", null))).contains("najwyżej 3");

        // zamknieta - nawet z wolnym miejscem
        wyslij("PUT", "ek_ala", "/api/crews/" + ekipa, formularz("Ekipa", 4, "OPEN", null)).andExpect(status().isOk());
        assertThat(karta("ek_dan", koncert, ekipa).get("canJoin").asBoolean()).isTrue();
        wyslij("PUT", "ek_ala", "/api/crews/" + ekipa + "/closed", Map.of("closed", true)).andExpect(status().isOk());
        assertThat(karta("ek_dan", koncert, ekipa).get("canJoin").asBoolean()).isFalse();
        assertThat(blad(wyslij("POST", "ek_dan", "/api/crews/" + ekipa + "/join", null))).contains("nie przyjmuje");
        wyslij("PUT", "ek_ala", "/api/crews/" + ekipa + "/closed", Map.of("closed", false)).andExpect(status().isOk());

        // w ekipie: druga ekipa na ten koncert i dolaczenie do innej - nie
        assertThat(blad(wyslij("POST", "ek_bob", "/api/events/" + koncert + "/crews", formularz("A", 4, "OPEN", null))))
            .contains("Jesteś już w ekipie");
        long innaEkipa = zaloz("ek_dan", koncert, 4, "OPEN");
        assertThat(karta("ek_bob", koncert, innaEkipa).get("canJoin").asBoolean()).isFalse();
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + innaEkipa + "/join", null))).contains("Jesteś już w ekipie");

        // kto jedzie z ekipa, ten idzie: rezygnacja i "zainteresowany" dopiero po odejsciu
        assertThat(blad(wyslij("DELETE", "ek_bob", "/api/events/" + koncert + "/participation", null)))
            .contains("najpierw odejdź z ekipy");
        assertThat(blad(wyslij("PUT", "ek_bob", "/api/events/" + koncert + "/participation",
            Map.of("status", "INTERESTED")))).contains("najpierw odejdź z ekipy");
        // ukrycie na liscie uczestnikow - wolno ("ide" zostaje)
        wyslij("PUT", "ek_bob", "/api/events/" + koncert + "/participation", Map.of("status", "GOING", "hidden", true))
            .andExpect(status().isOk());
        wyslij("DELETE", "ek_bob", "/api/crews/" + ekipa + "/members/me", null).andExpect(status().isNoContent());
        assertThat(zapis("ek_bob", koncert)).as("odejscie z ekipy nie wypisuje z koncertu").isEqualTo(ParticipationStatus.GOING);
        wyslij("DELETE", "ek_bob", "/api/events/" + koncert + "/participation", null).andExpect(status().isOk());
        assertThat(zapis("ek_bob", koncert)).isNull();
    }

    @Test
    @DisplayName("nabor za zgoda: prosba z wiadomoscia, cofniecie, odmowa na tydzien, przyjecie; wejscie konczy inne prosby")
    void approval() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 3, "APPROVAL");
        clearInvocations(push);

        JsonNode k = dolacz("ek_bob", ekipa, "  Jade z Pragi  ");
        assertThat(k.get("myState").asText()).isEqualTo("REQUESTED");
        assertThat(k.get("members").asInt()).isEqualTo(1);
        assertThat(members.findByCrewIdAndUserId(ekipa, id("ek_bob"))).isEmpty();
        assertThat(zapis("ek_bob", koncert)).as("prosba nie zapisuje na koncert").isNull();
        assertThat(powiadomienia("ek_ala", NotificationType.CREW_JOIN_REQUEST)).isEqualTo(1);
        verify(push).send(eq(id("ek_ala")), argThat(m -> m.url().equals("/ekipy/" + ekipa)
            && m.titleKey().equals("push.crewRequest.title")));
        JsonNode prosby = ekipa("ek_ala", ekipa).get("requests");
        assertThat(prosby).hasSize(1);
        assertThat(prosby.get(0).get("message").asText()).isEqualTo("Jade z Pragi");
        assertThat(ekipa("ek_bob", ekipa).get("requests")).as("prosby widzi tylko zakladajacy").isEmpty();
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/join", null))).contains("już czeka");

        // cofniecie - znika z dzwonka zakladajacego
        assertThat(tresc(wyslij("DELETE", "ek_bob", "/api/crews/" + ekipa + "/request", null)
            .andExpect(status().isOk())).get("myState").isNull()).isTrue();
        assertThat(powiadomienia("ek_ala", NotificationType.CREW_JOIN_REQUEST)).isZero();

        // odmowa: bez powiadomienia dla proszacego, przez tydzien nie poprosi ponownie
        dolacz("ek_bob", ekipa, null);
        long prosbaBoba = ekipa("ek_ala", ekipa).get("requests").get(0).get("id").asLong();
        assertThat(ekipa("ek_ala", ekipa).get("requests").get(0).get("message").isNull()).isTrue();
        wyslij("POST", "ek_cyd", "/api/crews/" + ekipa + "/requests/" + prosbaBoba + "/decline", null)
            .andExpect(status().isNotFound());
        wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + prosbaBoba + "/decline", null)
            .andExpect(status().isOk());
        assertThat(karta("ek_bob", koncert, ekipa).get("myState").asText()).isEqualTo("DECLINED");
        assertThat(karta("ek_bob", koncert, ekipa).get("canJoin").asBoolean()).isFalse();
        assertThat(powiadomienia("ek_ala", NotificationType.CREW_JOIN_REQUEST)).isZero();
        assertThat(notifications.findAll().stream().filter(n -> n.getRecipient().getUsername().equals("ek_bob"))).isEmpty();
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/join", null))).contains("niedawno odrzuciła");
        zegar.przesun(Duration.ofDays(6));
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/join", null))).contains("niedawno odrzuciła");
        zegar.przesun(Duration.ofDays(2));
        assertThat(karta("ek_bob", koncert, ekipa).get("myState").isNull()).as("po tygodniu odmowa nie wisi").isTrue();
        assertThat(dolacz("ek_bob", ekipa, "drugi raz").get("myState").asText()).isEqualTo("REQUESTED");

        // przyjecie: czlonek, zapis "ide", powiadomienie z push, prosba znika z dzwonka zakladajacego
        clearInvocations(push);
        long prosba = ekipa("ek_ala", ekipa).get("requests").get(0).get("id").asLong();
        wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/requests/" + prosba + "/accept", null)
            .andExpect(status().isNotFound());
        JsonNode po = tresc(wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + prosba + "/accept", null)
            .andExpect(status().isOk()));
        assertThat(loginy(po.get("members"))).containsExactly("ek_ala", "ek_bob");
        assertThat(po.get("requests")).isEmpty();
        assertThat(zapis("ek_bob", koncert)).isEqualTo(ParticipationStatus.GOING);
        assertThat(powiadomienia("ek_bob", NotificationType.CREW_REQUEST_ACCEPTED)).isEqualTo(1);
        assertThat(powiadomienia("ek_ala", NotificationType.CREW_JOIN_REQUEST)).isZero();
        verify(push).send(eq(id("ek_bob")), argThat(m -> m.url().equals("/ekipy/" + ekipa)));
        // przyjeta prosba drugi raz - juz jej nie ma
        wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + prosba + "/accept", null)
            .andExpect(status().isNotFound());

        // wejscie do ekipy konczy inne prosby na ten koncert
        dolacz("ek_dan", ekipa, null);
        assertThat(ekipa("ek_ala", ekipa).get("requests")).hasSize(1);
        zaloz("ek_dan", koncert, 4, "OPEN");
        assertThat(ekipa("ek_ala", ekipa).get("requests")).isEmpty();
        assertThat(powiadomienia("ek_ala", NotificationType.CREW_JOIN_REQUEST)).as("dzwonek tez").isEqualTo(1);

        // przyjecie kogos, kto w miedzyczasie jest w innej ekipie (wyscig) - odmowa i prosba znika
        dolacz("ek_ewa", ekipa, null);
        long prosbaEwy = ekipa("ek_ala", ekipa).get("requests").get(0).get("id").asLong();
        Crew danowa = crews.findAll().stream().filter(c -> c.getFounder().getUsername().equals("ek_dan")).findFirst().orElseThrow();
        members.save(new CrewMember(danowa, users.findByUsername("ek_ewa").orElseThrow(), CrewRole.MEMBER, LocalDateTime.now(), 0L));
        em.flush();
        assertThat(blad(wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + prosbaEwy + "/accept", null)))
            .contains("innej ekipy");
        assertThat(requests.findById(prosbaEwy)).isEmpty();

        // pelna: przyjecie ponad limit - nie
        dolacz("ek_cyd", ekipa, null);
        wyslij("PUT", "ek_ala", "/api/crews/" + ekipa, formularz("Ekipa", 2, "APPROVAL", null)).andExpect(status().isOk());
        long prosbaCyda = ekipa("ek_ala", ekipa).get("requests").get(0).get("id").asLong();
        assertThat(blad(wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + prosbaCyda + "/accept", null)))
            .contains("najwyżej 2");
    }

    /* ---------------------------- zarzad i odejscie ---------------------------- */

    @Test
    @DisplayName("zakladajacy: zmiany tylko on, limit nie mniejszy niz sklad, usuwanie z powiadomieniem; odejscie przekazuje ekipe")
    void founderAndLeaving() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 4, "OPEN");
        dolacz("ek_bob", ekipa, null);
        dolacz("ek_cyd", ekipa, null);
        dolacz("ek_dan", ekipa, null);

        assertThat(blad(wyslij("PUT", "ek_bob", "/api/crews/" + ekipa, formularz("Moja", 4, "OPEN", null))))
            .contains("tylko osoba, która założyła");
        wyslij("PUT", "ek_ewa", "/api/crews/" + ekipa, formularz("Moja", 4, "OPEN", null)).andExpect(status().isNotFound());
        assertThat(blad(wyslij("PUT", "ek_bob", "/api/crews/" + ekipa + "/closed", Map.of("closed", true))))
            .contains("tylko osoba, która założyła");
        assertThat(blad(wyslij("PUT", "ek_ala", "/api/crews/" + ekipa, formularz("Mniej", 3, "OPEN", null))))
            .contains("już 4 osób");
        JsonNode zmieniona = tresc(wyslij("PUT", "ek_ala", "/api/crews/" + ekipa, formularz("Nowa nazwa", 6, "APPROVAL", "Kraków"))
            .andExpect(status().isOk())).get("crew");
        assertThat(zmieniona.get("title").asText()).isEqualTo("Nowa nazwa");
        assertThat(zmieniona.get("joinPolicy").asText()).isEqualTo("APPROVAL");
        assertThat(zmieniona.get("departureCity").asText()).isEqualTo("Kraków");

        // usuniecie z ekipy: powiadomienie (z push) prowadzi do wydarzenia; zakladajacego usunac sie nie da
        clearInvocations(push);
        assertThat(blad(wyslij("DELETE", "ek_bob", "/api/crews/" + ekipa + "/members/ek_cyd", null)))
            .contains("tylko osoba, która założyła");
        JsonNode po = tresc(wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/members/ek_dan", null)
            .andExpect(status().isOk()));
        assertThat(loginy(po.get("members"))).doesNotContain("ek_dan");
        assertThat(powiadomienia("ek_dan", NotificationType.CREW_KICKED)).isEqualTo(1);
        verify(push).send(eq(id("ek_dan")), argThat(m -> m.url().equals("/wydarzenia/" + koncert + "#ekipy")));
        wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/members/ek_ala", null).andExpect(status().isNotFound());
        wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/members/ek_ewa", null).andExpect(status().isNotFound());
        assertThat(zapis("ek_dan", koncert)).as("usuniety dalej idzie na koncert").isEqualTo(ParticipationStatus.GOING);

        // zakladajaca odchodzi - ekipe przejmuje najdluzej obecna osoba
        wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/members/me", null).andExpect(status().isNoContent());
        JsonNode u = ekipa("ek_bob", ekipa);
        assertThat(u.get("crew").get("founder").asText()).isEqualTo("ek_bob");
        assertThat(u.get("crew").get("myState").asText()).isEqualTo("FOUNDER");
        assertThat(u.get("members").get(0).get("role").asText()).isEqualTo("FOUNDER");
        assertThat(loginy(u.get("members"))).containsExactly("ek_bob", "ek_cyd");
        // nowy zakladajacy zarzadza
        wyslij("PUT", "ek_bob", "/api/crews/" + ekipa, formularz("Bob prowadzi", 4, "OPEN", null)).andExpect(status().isOk());
        wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/members/me", null).andExpect(status().isNotFound());

        // ostatnia osoba odchodzi - ekipa znika razem z czatem i spotkaniami
        napisz("ek_cyd", ekipa, "do zobaczenia");
        long spotkanie = tresc(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat/meeting", spotkanie("Dworzec", 120, 30))
            .andExpect(status().isCreated())).get("meeting").get("id").asLong();
        wyslij("DELETE", "ek_cyd", "/api/crews/" + ekipa + "/members/me", null).andExpect(status().isNoContent());
        wyslij("DELETE", "ek_bob", "/api/crews/" + ekipa + "/members/me", null).andExpect(status().isNoContent());
        em.clear();
        assertThat(crews.findById(ekipa)).isEmpty();
        zapytaj("ek_bob", "/api/crews/" + ekipa).andExpect(status().isNotFound());
        assertThat(crewMessages.findAll()).isEmpty();
        assertThat(meetings.findById(spotkanie)).isEmpty();
        assertThat(notifications.findAll().stream().filter(n -> n.getCrew() != null)).isEmpty();
    }

    /* ---------------------------- blokady ---------------------------- */

    @Test
    @DisplayName("blokady: ekipa zakladajacego z blokady niewidoczna (404); osoby z blokad poza podgladem, ale w liczniku")
    void blocks() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 6, "OPEN");
        dolacz("ek_bob", ekipa, null);
        dolacz("ek_ewa", ekipa, null);
        napisz("ek_bob", ekipa, "od boba");
        napisz("ek_ewa", ekipa, "od ewy");

        // ewa blokuje boba: w ekipie zostaja oboje, ale ewa go nie widzi
        wyslij("PUT", "ek_ewa", "/api/blocks/ek_bob", null).andExpect(status().is2xxSuccessful());
        JsonNode k = karta("ek_ewa", koncert, ekipa);
        assertThat(k.get("members").asInt()).isEqualTo(3);
        assertThat(loginy(k.get("preview"))).containsExactly("ek_ala", "ek_ewa");
        assertThat(loginy(ekipa("ek_ewa", ekipa).get("members"))).doesNotContain("ek_bob");
        JsonNode czat = tresc(zapytaj("ek_ewa", "/api/crews/" + ekipa + "/chat").andExpect(status().isOk()));
        assertThat(czat).hasSize(1);
        assertThat(czat.get(0).get("content").asText()).isEqualTo("od ewy");
        // nieprzeczytane bez osob z blokad
        assertThat(ekipa("ek_ewa", ekipa).get("unreadChat").asLong()).isZero();
        assertThat(ekipa("ek_ala", ekipa).get("unreadChat").asLong()).isEqualTo(2);

        // zakladajaca blokuje dana: ekipy nie ma na liscie, strona i dolaczenie = 404
        wyslij("PUT", "ek_ala", "/api/blocks/ek_dan", null).andExpect(status().is2xxSuccessful());
        assertThat(karta("ek_dan", koncert, ekipa)).isNull();
        zapytaj("ek_dan", "/api/crews/" + ekipa).andExpect(status().isNotFound());
        wyslij("POST", "ek_dan", "/api/crews/" + ekipa + "/join", null).andExpect(status().isNotFound());
        // ...i w druga strone: dan blokuje kogos, kto zalozyl ekipe
        long ekipaCyda = zaloz("ek_cyd", koncert, 4, "OPEN");
        wyslij("PUT", "ek_dan", "/api/blocks/ek_cyd", null).andExpect(status().is2xxSuccessful());
        assertThat(karta("ek_dan", koncert, ekipaCyda)).isNull();
        wyslij("POST", "ek_dan", "/api/crews/" + ekipaCyda + "/join", null).andExpect(status().isNotFound());
    }

    /* ---------------------------- czat ---------------------------- */

    @Test
    @DisplayName("czat: tylko czlonkowie; nieprzeczytane i 'przeczytane'; push bez tresci dla tych bez nieprzeczytanych; usuwanie")
    void chat() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 6, "OPEN");
        dolacz("ek_bob", ekipa, null);
        dolacz("ek_cyd", ekipa, null);

        zapytaj("ek_dan", "/api/crews/" + ekipa + "/chat").andExpect(status().isNotFound());
        wyslij("POST", "ek_dan", "/api/crews/" + ekipa + "/chat", Map.of("content", "hej")).andExpect(status().isNotFound());
        wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat", Map.of("content", "  ")).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat", Map.of("content", "x".repeat(1001)))
            .andExpect(status().isUnprocessableEntity());

        clearInvocations(push);
        JsonNode pierwsza = napisz("ek_bob", ekipa, "  Zbiorka o 18 pod zegarem  ");
        assertThat(pierwsza.get("content").asText()).isEqualTo("Zbiorka o 18 pod zegarem");
        assertThat(pierwsza.get("mine").asBoolean()).isTrue();
        // push dla ali i cyda, bez tresci, nie dla piszacego
        verify(push).send(eq(id("ek_ala")), argThat(m -> m.tag().equals("crew-chat-" + ekipa)
            && m.url().equals("/ekipy/" + ekipa) && List.of(m.bodyArgs()).equals(List.of("ek_bob"))));
        verify(push).send(eq(id("ek_cyd")), any());
        verify(push, never()).send(eq(id("ek_bob")), any());

        // druga wiadomosc: ala i cyd maja juz nieprzeczytana - telefon nie brzeczy drugi raz
        clearInvocations(push);
        long druga = napisz("ek_bob", ekipa, "bilety mam").get("id").asLong();
        verify(push, never()).send(any(), any());
        assertThat(ekipa("ek_ala", ekipa).get("unreadChat").asLong()).isEqualTo(2);
        JsonNode moje = tresc(zapytaj("ek_ala", "/api/crews/mine").andExpect(status().isOk()));
        assertThat(moje).hasSize(1);
        assertThat(moje.get(0).get("unreadChat").asLong()).isEqualTo(2);
        assertThat(moje.get(0).get("eventId").asLong()).isEqualTo(koncert);
        assertThat(ekipa("ek_bob", ekipa).get("unreadChat").asLong()).as("wlasne sie nie licza").isZero();

        // ala przeczytala (znacznik nie wybiega poza czat i sie nie cofa) - nastepna znow daje push
        wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/chat/read", Map.of("upTo", druga + 1000)).andExpect(status().isNoContent());
        wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/chat/read", Map.of("upTo", 0)).andExpect(status().isNoContent());
        assertThat(ekipa("ek_ala", ekipa).get("unreadChat").asLong()).isZero();
        clearInvocations(push);
        long trzecia = napisz("ek_cyd", ekipa, "jestem").get("id").asLong();
        verify(push).send(eq(id("ek_ala")), any());
        // bob nie ma nic nieprzeczytanego (pisal ostatni) - dostaje; cyd pisze - nie
        verify(push).send(eq(id("ek_bob")), any());
        verify(push, never()).send(eq(id("ek_cyd")), any());
        assertThat(ekipa("ek_ala", ekipa).get("unreadChat").asLong()).isEqualTo(1);

        // lista: po i przed
        JsonNode po = tresc(zapytaj("ek_ala", "/api/crews/" + ekipa + "/chat?after=" + druga));
        assertThat(po).hasSize(1);
        assertThat(po.get(0).get("id").asLong()).isEqualTo(trzecia);
        JsonNode przed = tresc(zapytaj("ek_ala", "/api/crews/" + ekipa + "/chat?before=" + trzecia + "&limit=1"));
        assertThat(przed).hasSize(1);
        assertThat(przed.get(0).get("id").asLong()).isEqualTo(druga);

        // usuwanie: autor albo zakladajacy; slad; zmiany od podanej chwili
        String czas = tresc(zapytaj("ek_cyd", "/api/crews/" + ekipa + "/chat/changes")).get("serverTime").asText();
        assertThat(blad(wyslij("DELETE", "ek_cyd", "/api/crews/" + ekipa + "/chat/" + druga, null)))
            .contains("tylko osoba, która założyła");
        JsonNode lista = tresc(zapytaj("ek_ala", "/api/crews/" + ekipa + "/chat"));
        assertThat(lista.get(1).get("canDelete").asBoolean()).as("zakladajacy usuwa cudze").isTrue();
        assertThat(tresc(zapytaj("ek_cyd", "/api/crews/" + ekipa + "/chat")).get(1).get("canDelete").asBoolean()).isFalse();
        wyslij("DELETE", "ek_bob", "/api/crews/" + ekipa + "/chat/" + druga, null).andExpect(status().isNoContent());
        wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/chat/" + trzecia, null).andExpect(status().isNoContent());
        wyslij("DELETE", "ek_ala", "/api/crews/" + ekipa + "/chat/" + trzecia, null).andExpect(status().isNotFound());
        JsonNode zmiany = tresc(zapytaj("ek_cyd", "/api/crews/" + ekipa + "/chat/changes?since=" + czas));
        assertThat(zmiany.get("deletedIds").toString()).contains(String.valueOf(druga), String.valueOf(trzecia));
        JsonNode slad = tresc(zapytaj("ek_cyd", "/api/crews/" + ekipa + "/chat")).get(1);
        assertThat(slad.get("deleted").asBoolean()).isTrue();
        assertThat(slad.get("content").asText()).isEmpty();
        assertThat(ekipa("ek_ala", ekipa).get("unreadChat").asLong()).as("usuniete sie nie licza").isZero();
        zapytaj("ek_dan", "/api/crews/" + ekipa + "/chat/changes").andExpect(status().isNotFound());

        // po koncercie: dwa dni mozna pisac, potem tylko czytac; "moje ekipy" juz bez niego
        zegar.przesun(Duration.ofDays(22));
        napisz("ek_bob", ekipa, "dzieki za wieczor");
        assertThat(tresc(zapytaj("ek_bob", "/api/crews/mine"))).isEmpty();
        zegar.przesun(Duration.ofDays(1));
        assertThat(ekipa("ek_bob", ekipa).get("chatOpen").asBoolean()).isFalse();
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat", Map.of("content", "halo"))))
            .contains("tylko do czytania");
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat/meeting", spotkanie("X", 60, 0))))
            .contains("tylko do czytania");
        zapytaj("ek_bob", "/api/crews/" + ekipa + "/chat").andExpect(status().isOk());
        // na minione nikt nowy nie dolaczy
        assertThat(blad(wyslij("POST", "ek_dan", "/api/crews/" + ekipa + "/join", null))).contains("już się odbyło");
    }

    /* ---------------------------- spotkania ekipy ---------------------------- */

    @Test
    @DisplayName("spotkanie ekipy: widza i odpowiadaja czlonkowie; przypomnienie prowadzi do ekipy; odejscie zdejmuje odpowiedz")
    void crewMeeting() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 6, "OPEN");
        dolacz("ek_bob", ekipa, null);
        dolacz("ek_cyd", ekipa, null);
        String czas = tresc(zapytaj("ek_ala", "/api/crews/" + ekipa + "/chat/changes")).get("serverTime").asText();

        JsonNode m = tresc(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat/meeting", spotkanie("Pod zegarem", 120, 30))
            .andExpect(status().isCreated()));
        assertThat(m.get("content").asText()).isEmpty();
        long spotkanie = m.get("meeting").get("id").asLong();
        assertThat(m.get("meeting").get("place").asText()).isEqualTo("Pod zegarem");
        assertThat(m.get("meeting").get("canRespond").asBoolean()).isTrue();

        zapytaj("ek_dan", "/api/meetings/" + spotkanie).andExpect(status().isNotFound());
        wyslij("PUT", "ek_dan", "/api/meetings/" + spotkanie + "/rsvp", Map.of("status", "GOING")).andExpect(status().isNotFound());
        zapytaj("ek_cyd", "/api/meetings/" + spotkanie).andExpect(status().isOk());
        wyslij("PUT", "ek_cyd", "/api/meetings/" + spotkanie + "/rsvp", Map.of("status", "GOING")).andExpect(status().isOk());
        wyslij("PUT", "ek_ala", "/api/meetings/" + spotkanie + "/rsvp", Map.of("status", "GOING")).andExpect(status().isOk());

        // odpowiedzi dochodza do otwartych czatow ekipy
        JsonNode zmiany = tresc(zapytaj("ek_bob", "/api/crews/" + ekipa + "/chat/changes?since=" + czas));
        assertThat(zmiany.get("meetings")).hasSize(1);
        assertThat(zmiany.get("meetings").get(0).get("goingCount").asInt()).isEqualTo(3);
        // spotkanie jest w liscie czatu
        JsonNode lista = tresc(zapytaj("ek_cyd", "/api/crews/" + ekipa + "/chat"));
        assertThat(lista.get(0).get("meeting").get("myStatus").asText()).isEqualTo("GOING");

        // kto odszedl z ekipy, ten nie ma odpowiedzi ani przypomnienia
        wyslij("DELETE", "ek_cyd", "/api/crews/" + ekipa + "/members/me", null).andExpect(status().isNoContent());
        assertThat(attendees.forMeetings(List.of(spotkanie)).stream().map(a -> a.getUser().getUsername()))
            .containsExactlyInAnyOrder("ek_bob", "ek_ala");
        zapytaj("ek_cyd", "/api/meetings/" + spotkanie).andExpect(status().isNotFound());

        // skasowanie rozmow nie zabiera spotkan ekipy
        meetingService.deleteOrphans();
        em.flush();
        assertThat(meetings.findById(spotkanie)).isPresent();

        // przypomnienie: dla potwierdzonych czlonkow, link do ekipy
        zegar.przesun(Duration.ofMinutes(91));
        assertThat(reminders.run()).isEqualTo(2);
        em.flush();
        JsonNode dzwonek = tresc(zapytaj("ek_ala", "/api/notifications"));
        JsonNode przypomnienie = null;
        for (JsonNode n : dzwonek.get("content")) {
            if ("MEETING_REMINDER".equals(n.get("type").asText())) {
                przypomnienie = n;
            }
        }
        assertThat(przypomnienie).isNotNull();
        assertThat(przypomnienie.get("link").asText()).isEqualTo("/ekipy/" + ekipa);
        assertThat(przypomnienie.get("crewId").asLong()).isEqualTo(ekipa);

        // odwolanie: link tez do ekipy (a nie do rozmowy z zakladajacym)
        zegar.zeruj();
        long drugie = tresc(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat/meeting", spotkanie("Bramka", 200, 0))
            .andExpect(status().isCreated())).get("meeting").get("id").asLong();
        wyslij("PUT", "ek_ala", "/api/meetings/" + drugie + "/rsvp", Map.of("status", "GOING")).andExpect(status().isOk());
        wyslij("POST", "ek_bob", "/api/meetings/" + drugie + "/cancel", null).andExpect(status().isOk());
        assertThat(notifications.findAll().stream()
            .filter(n -> n.getType() == NotificationType.MEETING_CANCELLED)
            .map(n -> new com.musicclubapp.mapper.NotificationMapper().link(n)))
            .containsExactly("/ekipy/" + ekipa);

        // usuniecie wiadomosci ze spotkaniem kasuje spotkanie
        long wiadomosc = tresc(zapytaj("ek_bob", "/api/crews/" + ekipa + "/chat")).get(0).get("id").asLong();
        wyslij("DELETE", "ek_bob", "/api/crews/" + ekipa + "/chat/" + wiadomosc, null).andExpect(status().isNoContent());
        em.clear();
        assertThat(meetings.findById(spotkanie)).isEmpty();
    }

    /* ---------------------------- eksport, usuniecie konta, wydarzenie ---------------------------- */

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("eksport: ekipy, prosby i wiadomosci; usuniecie konta przekazuje ekipe albo ja rozwiazuje")
    void exportAndDeletion() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 6, "OPEN");
        dolacz("ek_bob", ekipa, null);
        dolacz("ek_cyd", ekipa, null);
        napisz("ek_ala", ekipa, "od ali");
        napisz("ek_bob", ekipa, "od boba");
        long drugi = wydarzenie("EK-2", 10);
        long sama = zaloz("ek_ala", drugi, 4, "OPEN");
        long zgoda = zaloz("ek_dan", drugi, 4, "APPROVAL");
        dolacz("ek_ewa", zgoda, "wezmiecie mnie?");

        Map<String, Object> dane = (Map<String, Object>) export.przygotuj("ek_ala", "haslo123").dane().get("crews");
        JsonNode ala = json.valueToTree(dane);
        assertThat(ala.get("memberships")).hasSize(2);
        assertThat(ala.get("memberships").get(0).get("role").asText()).isEqualTo("FOUNDER");
        assertThat(ala.get("memberships").get(0).get("title").asText()).isEqualTo("Ekipa ek_ala");
        assertThat(ala.get("messages")).hasSize(1);
        assertThat(ala.get("messages").get(0).get("content").asText()).isEqualTo("od ali");
        JsonNode bob = json.valueToTree(export.przygotuj("ek_bob", "haslo123").dane().get("crews"));
        assertThat(bob.get("memberships").get(0).get("role").asText()).isEqualTo("MEMBER");
        assertThat(bob.get("memberships").get(0).has("title")).as("cudzej ekipy opis nie jest moja dana").isFalse();
        JsonNode ewa = json.valueToTree(export.przygotuj("ek_ewa", "haslo123").dane().get("crews"));
        assertThat(ewa.get("requests").get(0).get("message").asText()).isEqualTo("wezmiecie mnie?");

        deletion.erase(users.findByUsername("ek_ala").orElseThrow());
        em.flush();
        em.clear();
        Crew przejeta = crews.findById(ekipa).orElseThrow();
        assertThat(przejeta.getFounder().getUsername()).isEqualTo("ek_bob");
        assertThat(crews.findById(sama)).as("sama w ekipie - ekipa znika").isEmpty();
        assertThat(crewMessages.findAll().stream().map(m -> m.getContent())).containsExactly("od boba");
        assertThat(loginy(ekipa("ek_bob", ekipa).get("members"))).containsExactly("ek_bob", "ek_cyd");

        deletion.erase(users.findByUsername("ek_ewa").orElseThrow());
        em.flush();
        assertThat(requests.findAll()).isEmpty();
    }

    @Test
    @DisplayName("wydarzenie skasowane (miesiac po dacie): ekipy, czat, prosby, spotkania i powiadomienia znikaja razem z nim")
    void eventDeletionCascades() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 6, "APPROVAL");
        dolacz("ek_bob", ekipa, null);
        long prosba = ekipa("ek_ala", ekipa).get("requests").get(0).get("id").asLong();
        wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + prosba + "/accept", null).andExpect(status().isOk());
        dolacz("ek_cyd", ekipa, null);
        napisz("ek_bob", ekipa, "hej");
        wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat/meeting", spotkanie("Dworzec", 120, 30))
            .andExpect(status().isCreated());
        assertThat(notifications.findAll().stream().filter(n -> n.getCrew() != null)).isNotEmpty();
        em.flush();
        em.clear();

        // tak jak import (removeOld): najpierw zapisy, potem samo wydarzenie
        participations.deleteByEventIds(List.of(koncert));
        events.delete(events.findById(koncert).orElseThrow());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crews", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crew_members", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crew_requests", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crew_messages", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meetings", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE crew_id IS NOT NULL", Long.class)).isZero();
    }

    @Test
    @DisplayName("sprzatanie prosb: odrzucone po tygodniu, czekajace na koncert, ktory minal (znikaja tez z dzwonka)")
    void requestCleanup() throws Exception {
        long blisko = wydarzenie("EK-B", 3);
        long ekipa = zaloz("ek_ala", blisko, 6, "APPROVAL");
        dolacz("ek_bob", ekipa, null);
        long odrzucona = ekipa("ek_ala", ekipa).get("requests").get(0).get("id").asLong();
        wyslij("POST", "ek_ala", "/api/crews/" + ekipa + "/requests/" + odrzucona + "/decline", null).andExpect(status().isOk());
        dolacz("ek_cyd", ekipa, null);
        long dalekoEkipa = zaloz("ek_ala", koncert, 6, "APPROVAL");
        dolacz("ek_dan", dalekoEkipa, null);

        cleanup.clean();
        em.flush();
        assertThat(requests.findAll()).hasSize(3);

        // 4 dni: koncert minal (czekajaca cyda znika z dzwonkiem), odrzucona jeszcze w karencji
        zegar.przesun(Duration.ofDays(4));
        cleanup.clean();
        em.flush();
        assertThat(requests.findAll().stream().map(r -> r.getUser().getUsername())).containsExactlyInAnyOrder("ek_bob", "ek_dan");
        assertThat(notifications.findAll().stream().filter(n -> n.getType() == NotificationType.CREW_JOIN_REQUEST)
            .map(n -> n.getActor().getUsername())).containsExactly("ek_dan");

        zegar.przesun(Duration.ofDays(4));
        cleanup.clean();
        em.flush();
        assertThat(requests.findAll().stream().map(r -> r.getUser().getUsername())).containsExactly("ek_dan");
    }

    @Test
    @DisplayName("zakazy: z zakazem publikowania nie zalozysz ani nie zmienisz ekipy, z zakazem wiadomosci nie piszesz na czacie")
    void bans() throws Exception {
        long ekipa = zaloz("ek_ala", koncert, 6, "OPEN");
        dolacz("ek_bob", ekipa, null);
        users.findByUsername("ek_ala").orElseThrow().setBannedUntil(com.musicclubapp.entity.BanKind.POSTING,
            LocalDateTime.now().plusDays(1));
        users.findByUsername("ek_cyd").orElseThrow().setBannedUntil(com.musicclubapp.entity.BanKind.POSTING,
            LocalDateTime.now().plusDays(1));
        users.findByUsername("ek_bob").orElseThrow().setBannedUntil(com.musicclubapp.entity.BanKind.MESSAGING,
            LocalDateTime.now().plusDays(1));
        em.flush();

        assertThat(blad(wyslij("PUT", "ek_ala", "/api/crews/" + ekipa, formularz("Nowa", 6, "OPEN", null))))
            .contains("zakaz publikowania");
        assertThat(blad(wyslij("POST", "ek_cyd", "/api/events/" + koncert + "/crews", formularz("A", 4, "OPEN", null))))
            .contains("zakaz publikowania");
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat", Map.of("content", "hej"))))
            .contains("zakaz wysyłania wiadomości");
        assertThat(blad(wyslij("POST", "ek_bob", "/api/crews/" + ekipa + "/chat/meeting", spotkanie("X", 60, 0))))
            .contains("zakaz wysyłania wiadomości");
        // zakaz publikowania nie zamyka czatu, zakaz wiadomosci nie zamyka dolaczania
        napisz("ek_ala", ekipa, "dalej moge pisac");
        assertThat(crewMessages.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("lista pod wydarzeniem: moja pierwsza, potem z miejscami (od najblizszej), pelne na koncu; znajomi w srodku")
    void ordering() throws Exception {
        long daleka = tresc(wyslij("POST", "ek_bob", "/api/events/" + koncert + "/crews",
            formularz("Z Gdanska", 4, "OPEN", "Gdańsk")).andExpect(status().isCreated())).get("crew").get("id").asLong();
        long bliska = tresc(wyslij("POST", "ek_cyd", "/api/events/" + koncert + "/crews",
            formularz("Z Piaseczna", 4, "OPEN", "Piaseczno")).andExpect(status().isCreated())).get("crew").get("id").asLong();
        long pelna = tresc(wyslij("POST", "ek_dan", "/api/events/" + koncert + "/crews",
            formularz("Z Warszawy", 2, "OPEN", "Warszawa")).andExpect(status().isCreated())).get("crew").get("id").asLong();
        dolacz("ek_ewa", pelna, null);
        location.update("ek_ala", "Warszawa");
        em.flush();

        List<Long> kolejnosc = new ArrayList<>();
        JsonNode lista = tresc(zapytaj("ek_ala", "/api/events/" + koncert + "/crews"));
        lista.forEach(k -> kolejnosc.add(k.get("id").asLong()));
        // z wolnymi miejscami od najblizszej (Piaseczno, potem Gdansk); pelna z Warszawy na koncu, choc najblizej
        assertThat(kolejnosc).containsExactly(bliska, daleka, pelna);

        long moja = zaloz("ek_ala", koncert, 4, "OPEN");
        kolejnosc.clear();
        tresc(zapytaj("ek_ala", "/api/events/" + koncert + "/crews")).forEach(k -> kolejnosc.add(k.get("id").asLong()));
        assertThat(kolejnosc.get(0)).isEqualTo(moja);

        // znajomi w srodku: licznik dla ogladajacego
        User ala = users.findByUsername("ek_ala").orElseThrow();
        ala.addFriend(users.findByUsername("ek_ewa").orElseThrow());
        users.save(ala);
        em.flush();
        assertThat(karta("ek_ala", koncert, pelna).get("friends").asLong()).isEqualTo(1);
        assertThat(karta("ek_ala", koncert, daleka).get("friends").asLong()).isZero();
        zapytaj("ek_ala", "/api/events/999999/crews").andExpect(status().isNotFound());
    }
}
