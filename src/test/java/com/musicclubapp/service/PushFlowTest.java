package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.push.P256;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.PushSubscriptionRepository;
import com.musicclubapp.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Powiadomienia push i przypomnienia przez prawdziwe API, z udawana usluga
 * push na localhost. Bez @Transactional - wysylka idzie dopiero po
 * zatwierdzeniu transakcji, wiec test sprzata po sobie sam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    // Klucze tylko do testow - wygenerowane osobno, nigdzie indziej nieuzywane
    "app.push.public-key=BElEHL0XmXzbvTX7dsRg3gelKadq5OATDGbBkAWrN4GQAS-yQtEocM3nmnAZrzplJbAiI51-M314MKVpt_YY-iw",
    "app.push.private-key=DV8q7yoaMeZ0fLbvWdOKi5_qgFZ3mw8nNnJNyGCwpgk",
    "app.push.subject=mailto:test@example.com",
    "app.push.extra-hosts=127.0.0.1"
})
@DisplayName("Push i przypomnienia - caly przebieg")
class PushFlowTest {

    /** Klucze "przegladarki" - prywatny potrzebny, zeby test mogl odczytac wiadomosc. */
    static final String P256DH =
        "BAzQVpKF2Dj2L36J0Svdj06MU4z7mulO5s9DNZIkMw10lmtpOcIbUc9GkkcLK0j3koFEeEAShdbkuGXgU19rwZ8";
    static final String PRYWATNY = "qYN_Tj_egGOaFEAnZPi1hRehbXmX2FRddGIyYjvUyac";
    static final String AUTH = "sSY-_7AZFeJuaaGARiKweQ";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private MusicEventRepository events;
    @Autowired private EventParticipationRepository participations;
    @Autowired private NotificationRepository notifications;
    @Autowired private PushSubscriptionRepository subscriptions;
    @Autowired private EventReminderService reminders;
    @Autowired private EventImportService importer;
    @Autowired private UserService userService;
    @Autowired private PlatformTransactionManager tx;
    @Autowired private AccountDeletionService deletion;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private com.musicclubapp.repository.FriendRequestRepository friendRequests;

    /** Co doszlo do udawanej uslugi push. */
    record Dostawa(String sciezka, Map<String, String> naglowki, byte[] tresc) {
    }

    private HttpServer usluga;
    private final List<Dostawa> dostawy = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> kody = new ConcurrentHashMap<>();
    private final List<Long> doSprzatniecia = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        usluga = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        usluga.createContext("/", w -> {
            Map<String, String> h = new java.util.HashMap<>();
            w.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            dostawy.add(new Dostawa(w.getRequestURI().getPath(), h, w.getRequestBody().readAllBytes()));
            w.sendResponseHeaders(kody.getOrDefault(w.getRequestURI().getPath(), 201), -1);
            w.close();
        });
        usluga.start();
    }

    @AfterEach
    void tearDown() {
        usluga.stop(0);
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            for (Long id : doSprzatniecia) {
                subscriptions.deleteAllOfUser(id);
                notifications.deleteByUserId(id);
                participations.deleteByUserId(id);
                friendRequests.deleteBySenderIdOrRecipientId(id, id);
            }
            doSprzatniecia.forEach(users::deleteById);
        });
        events.findAll().stream().filter(e -> e.getExternalId().startsWith("PF")).forEach(events::delete);
    }

    private User konto(String login) {
        User u = users.save(new User(login, login + "@example.com", "x"));
        doSprzatniecia.add(u.getId());
        return u;
    }

    private String adres(String sciezka) {
        return "http://127.0.0.1:" + usluga.getAddress().getPort() + sciezka;
    }

    private ResultActions zapisz(String kto, String endpoint, String p256dh) throws Exception {
        return mvc.perform(post("/api/push/subscriptions").with(user(kto)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("endpoint", endpoint, "p256dh", p256dh, "auth", AUTH, "lang", "pl"))));
    }

    private MusicEvent wydarzenie(String id, int zaIleDni) {
        return events.save(WydarzeniaTestowe.wydarzenie(id, "Koncert " + id,
            importer.today().plusDays(zaIleDni), "Kraków", "Rock", null, "Zespol"));
    }

    private void idzie(User kto, MusicEvent e) {
        participations.save(new EventParticipation(e, kto, ParticipationStatus.GOING, false, LocalDateTime.now()));
    }

    /** Czeka, az do uslugi dojdzie tyle wiadomosci (wysylka idzie w osobnym watku). */
    private List<Dostawa> czekajNa(int ile) throws InterruptedException {
        for (int i = 0; i < 100 && dostawy.size() < ile; i++) {
            Thread.sleep(50);
        }
        Thread.sleep(150);
        return List.copyOf(dostawy);
    }

    @Test
    @DisplayName("ustawienia: klucz serwera; zapis tylko do uslugi z listy i z poprawnym kluczem")
    void subscribeValidation() throws Exception {
        konto("pf_ala");

        mvc.perform(get("/api/push").with(user("pf_ala")))
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.publicKey").value(org.hamcrest.Matchers.startsWith("BElEHL0X")))
            .andExpect(jsonPath("$.eventReminders").value(true))
            .andExpect(jsonPath("$.devices").value(0));

        // Adres spoza uslug push - serwer nie bedzie tam niczego wysylal
        zapisz("pf_ala", "https://intranet.example.com/push/1", P256DH).andExpect(status().isConflict());
        zapisz("pf_ala", "http://169.254.169.254/latest", P256DH).andExpect(status().isConflict());
        zapisz("pf_ala", "https://user@fcm.googleapis.com/fcm/send/x", P256DH).andExpect(status().isConflict());
        // Klucz spoza krzywej
        byte[] zly = P256.fromBase64(P256DH);
        zly[64] ^= 1;
        zapisz("pf_ala", "https://fcm.googleapis.com/fcm/send/x", P256.base64(zly)).andExpect(status().isConflict());

        zapisz("pf_ala", adres("/push/ala"), P256DH).andExpect(status().isNoContent());
        zapisz("pf_ala", adres("/push/ala"), P256DH).andExpect(status().isNoContent());
        mvc.perform(get("/api/push").with(user("pf_ala"))).andExpect(jsonPath("$.devices").value(1));

        // Cudzego urzadzenia wypisac nie mozna
        konto("pf_obcy");
        mvc.perform(delete("/api/push/subscriptions").with(user("pf_obcy")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + adres("/push/ala") + "\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/push").with(user("pf_ala"))).andExpect(jsonPath("$.devices").value(1));
    }

    @Test
    @DisplayName("przypomnienia: za 3 dni i jutro, raz na prog; w dzwonku i na telefonie, zaszyfrowane i podpisane")
    void reminders() throws Exception {
        User ala = konto("pf_ala");
        User bob = konto("pf_bob");
        MusicEvent za3 = wydarzenie("PF3", 3);
        MusicEvent jutro = wydarzenie("PF1", 1);
        MusicEvent za5 = wydarzenie("PF5", 5);
        MusicEvent wycofane = wydarzenie("PFW", 1);
        wycofane.withdraw(LocalDateTime.now());
        events.save(wycofane);
        idzie(ala, za3);
        idzie(ala, jutro);
        idzie(ala, za5);
        idzie(ala, wycofane);
        idzie(bob, jutro);
        zapisz("pf_ala", adres("/push/ala"), P256DH).andExpect(status().isNoContent());
        zapisz("pf_bob", adres("/push/bob"), P256DH).andExpect(status().isNoContent());
        mvc.perform(put("/api/push/reminders").with(user("pf_bob")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"eventReminders\":false}"))
            .andExpect(jsonPath("$.eventReminders").value(false));

        assertThat(reminders.run()).isEqualTo(2);
        assertThat(reminders.run()).as("drugi przebieg - nic nowego").isZero();

        // W dzwonku - mimo ze przypomnienie nie ma sprawcy
        JsonNode dzwonek = json.readTree(mvc.perform(get("/api/notifications").with(user("pf_ala")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(dzwonek.get("content")).hasSize(2);
        JsonNode pierwsze = dzwonek.get("content").get(0);
        assertThat(pierwsze.get("type").asText()).isEqualTo("EVENT_REMINDER");
        assertThat(pierwsze.get("actorUsername").isNull()).isTrue();
        assertThat(List.of(dzwonek.get("content").get(0).get("daysLeft").asInt(),
            dzwonek.get("content").get(1).get("daysLeft").asInt())).containsExactlyInAnyOrder(1, 3);
        assertThat(dzwonek.findValuesAsText("link"))
            .containsExactlyInAnyOrder("/wydarzenia/" + za3.getId(), "/wydarzenia/" + jutro.getId());

        // Na telefonie: tylko Ala (Bob wylaczyl przypomnienia)
        List<Dostawa> doszlo = czekajNa(2);
        assertThat(doszlo).hasSize(2).allMatch(d -> d.sciezka().equals("/push/ala"));
        Dostawa d = doszlo.get(0);
        assertThat(d.naglowki()).containsEntry("content-encoding", "aes128gcm").containsEntry("ttl", "86400");
        assertThat(d.naglowki().get("authorization")).startsWith("vapid t=")
            .endsWith(", k=BElEHL0XmXzbvTX7dsRg3gelKadq5OATDGbBkAWrN4GQAS-yQtEocM3nmnAZrzplJbAiI51-M314MKVpt_YY-iw");
        List<String> tytuly = doszlo.stream().map(x -> odczytaj(x.tresc()).get("title").asText()).toList();
        assertThat(tytuly).containsExactlyInAnyOrder("Za 3 dni: Koncert PF3", "Jutro: Koncert PF1");
        JsonNode jutrzejsze = doszlo.stream().map(x -> odczytaj(x.tresc()))
            .filter(n -> n.get("title").asText().startsWith("Jutro")).findFirst().orElseThrow();
        assertThat(jutrzejsze.get("body").asText()).isEqualTo("Klub PF1 · 20:00");
        assertThat(jutrzejsze.get("url").asText()).isEqualTo("/wydarzenia/" + jutro.getId());
        assertThat(jutrzejsze.get("tag").asText()).isEqualTo("event-" + jutro.getId());
    }

    @Test
    @DisplayName("nastepny prog zastepuje poprzednie przypomnienie; zapis tuz przed wydarzeniem nie przypomina od razu")
    void nextThresholdAndFreshSignup() throws Exception {
        User ala = konto("pf_ala");
        MusicEvent jutro = wydarzenie("PF1", 1);
        // Zapis sprzed kilku dni, "za 3 dni" juz bylo
        EventParticipation stary = new EventParticipation(jutro, ala, ParticipationStatus.INTERESTED, false, LocalDateTime.now());
        stary.markReminded(3);
        participations.save(stary);

        assertThat(reminders.run()).isEqualTo(1);
        assertThat(notifications.findAll().stream().filter(n -> n.getRecipient().getId().equals(ala.getId())).count())
            .isEqualTo(1);

        // Swiezy zapis przez API na jutro: bez przypomnienia - wiadomo, kiedy jest
        konto("pf_ewa");
        MusicEvent pojutrze = wydarzenie("PF2", 2);
        mvc.perform(put("/api/events/" + pojutrze.getId() + "/participation").with(user("pf_ewa")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOING\"}")).andExpect(status().isOk());
        assertThat(reminders.run()).isZero();

        // Rezygnacja zdejmuje przypomnienie z dzwonka
        mvc.perform(delete("/api/events/" + jutro.getId() + "/participation").with(user("pf_ala")).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").with(user("pf_ala"))).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("martwy adres (410) znika; po 'wyloguj z innych urzadzen' stare urzadzenia nic nie dostaja")
    void goneAndRevoked() throws Exception {
        konto("pf_ala");
        zapisz("pf_ala", adres("/push/martwy"), P256DH).andExpect(status().isNoContent());
        zapisz("pf_ala", adres("/push/zywy"), P256DH).andExpect(status().isNoContent());
        kody.put("/push/martwy", 410);

        mvc.perform(post("/api/push/test").with(user("pf_ala")).with(csrf())).andExpect(status().isNoContent());
        assertThat(czekajNa(2)).hasSize(2);
        assertThat(subscriptions.findByEndpoint(adres("/push/martwy"))).isEmpty();
        assertThat(odczytaj(dostawy.get(0).tresc()).get("title").asText()).isEqualTo("Powiadomienia działają");
        // Probne - najwyzej raz na pol minuty
        mvc.perform(post("/api/push/test").with(user("pf_ala")).with(csrf())).andExpect(status().isTooManyRequests());

        userService.revokeOtherSessions("pf_ala");
        mvc.perform(get("/api/push").with(user("pf_ala"))).andExpect(jsonPath("$.devices").value(0));
        mvc.perform(post("/api/push/test").with(user("pf_ala")).with(csrf())).andExpect(status().isConflict());

        // Zaproszenie od Boba: w dzwonku jest, ale na stare urzadzenie nie idzie nic
        konto("pf_bob");
        int bylo = dostawy.size();
        mvc.perform(post("/api/friends/requests").with(user("pf_bob")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"pf_ala\"}"))
            .andExpect(status().is2xxSuccessful());
        Thread.sleep(600);
        assertThat(dostawy).hasSize(bylo);

        // To urzadzenie zapisuje sie ponownie (aplikacja robi to sama przy otwarciu) - i znow dostaje
        zapisz("pf_ala", adres("/push/zywy"), P256DH).andExpect(status().isNoContent());
        konto("pf_ewa");
        mvc.perform(post("/api/friends/requests").with(user("pf_ewa")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"pf_ala\"}"))
            .andExpect(status().is2xxSuccessful());
        List<Dostawa> po = czekajNa(bylo + 1);
        assertThat(po).hasSize(bylo + 1);
        JsonNode zaproszenie = odczytaj(po.get(po.size() - 1).tresc());
        assertThat(zaproszenie.get("body").asText()).isEqualTo("pf_ewa chce dodać cię do znajomych");
        assertThat(zaproszenie.get("url").asText()).isEqualTo("/znajomi");
    }

    @Test
    @DisplayName("konto sprzed znacznikow bezpieczenstwa (NULL w bazie) tez wlacza i dostaje powiadomienia")
    void legacyAccountWithoutStamp() throws Exception {
        User stary = konto("pf_stary");
        new TransactionTemplate(tx).executeWithoutResult(s -> jdbc.update(
            "UPDATE users SET security_stamp = NULL WHERE id = ?", stary.getId()));

        zapisz("pf_stary", adres("/push/stary"), P256DH).andExpect(status().isNoContent());
        mvc.perform(get("/api/push").with(user("pf_stary"))).andExpect(jsonPath("$.devices").value(1));
        mvc.perform(post("/api/push/test").with(user("pf_stary")).with(csrf())).andExpect(status().isNoContent());
        assertThat(czekajNa(1)).hasSize(1);
    }

    @Test
    @DisplayName("usuniecie konta z zapisanym urzadzeniem - urzadzenie znika razem z kontem")
    void accountDeletionRemovesDevices() throws Exception {
        User ala = konto("pf_ala");
        zapisz("pf_ala", adres("/push/ala"), P256DH).andExpect(status().isNoContent());

        new TransactionTemplate(tx).executeWithoutResult(s -> deletion.erase(users.findById(ala.getId()).orElseThrow()));
        doSprzatniecia.remove(ala.getId());

        assertThat(users.findById(ala.getId())).isEmpty();
        assertThat(subscriptions.findByEndpoint(adres("/push/ala"))).isEmpty();
    }

    /* ------------------------------------------------------------------ */
    /*  Strona przegladarki: odszyfrowanie (RFC 8291) - tylko do testu      */
    /* ------------------------------------------------------------------ */

    private JsonNode odczytaj(byte[] body) {
        try {
            byte[] salt = Arrays.copyOf(body, 16);
            int idlen = body[20] & 0xff;
            byte[] nadawca = Arrays.copyOfRange(body, 21, 21 + idlen);
            byte[] szyfr = Arrays.copyOfRange(body, 21 + idlen, body.length);
            byte[] odbiorca = P256.fromBase64(P256DH);

            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(P256.privateKey(P256.fromBase64(PRYWATNY)));
            ecdh.doPhase(P256.publicKey(nadawca), true);
            byte[] prkKey = hmac(P256.fromBase64(AUTH), ecdh.generateSecret());
            ByteArrayOutputStream info = new ByteArrayOutputStream();
            info.writeBytes("WebPush: info\0".getBytes(StandardCharsets.US_ASCII));
            info.writeBytes(odbiorca);
            info.writeBytes(nadawca);
            info.write(1);
            byte[] prk = hmac(salt, hmac(prkKey, info.toByteArray()));
            byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] jawne = aes.doFinal(szyfr);
            assertThat(ByteBuffer.wrap(body, 16, 4).getInt()).isEqualTo(4096);
            assertThat(jawne[jawne.length - 1]).isEqualTo((byte) 2);
            return json.readTree(new String(jawne, 0, jawne.length - 1, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("Nie da sie odczytac wiadomosci push", e);
        }
    }

    private static byte[] hmac(byte[] klucz, byte[] dane) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(klucz, "HmacSHA256"));
        return mac.doFinal(dane);
    }
}
