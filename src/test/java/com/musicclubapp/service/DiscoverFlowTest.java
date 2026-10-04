package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.FriendRequest;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.SwipeDecision;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.DiscoverSwipeRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tryb Poznawaj przez prawdziwe API: kto jest w talii i w jakiej kolejnosci, zasieg, karta, decyzje, wzajemne
 * "tak" = znajomosc, cofanie, wygasanie "nie", limit dzienny i blokady.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "app.discover.swipes-per-day=6")
@DisplayName("Tryb Poznawaj - talia, decyzje i wzajemne tak")
class DiscoverFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ArtistRepository artists;
    @Autowired private DiscoverSwipeRepository swipes;
    @Autowired private FriendRequestRepository requests;
    @Autowired private NotificationRepository notifications;
    @Autowired private DiscoverService discover;
    @Autowired private EntityManager em;

    @MockBean private PushService push;

    private User ja;
    private Artist radiohead;
    private Artist bjork;
    private Artist kult;

    @BeforeEach
    void setUp() {
        radiohead = artists.save(new Artist("dc-1", "Radiohead", "https://img/radiohead.jpg"));
        bjork = artists.save(new Artist("dc-2", "Björk", null));
        kult = artists.save(new Artist("dc-3", "Kult", null));
        ja = osoba("dc_ja", "Poznań", 52.4064, 16.9252, true);
        ja.getFavoriteArtists().add(radiohead);
        ja.getFavoriteArtists().add(bjork);
        users.save(ja);
        em.flush();
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private User osoba(String login, String miasto, Double lat, Double lon, boolean poznawaj) {
        User u = new User(login, login + "@example.com", "x");
        u.markEmailVerified(LocalDateTime.now());
        if (miasto != null) {
            u.setCity(miasto, EventImportService.cityKey(miasto), lat, lon);
        }
        u.setDiscover(poznawaj, 100);
        return users.save(u);
    }

    private User poznan(String login) {
        return osoba(login, "Poznań", 52.4064, 16.9252, true);
    }

    private ResultActions zapytaj(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String kto, String adres, Object tresc, boolean put) throws Exception {
        var z = put ? put(adres) : post(adres);
        return mvc.perform(z.with(user(kto)).with(csrf()).header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc)));
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> talia(String kto) throws Exception {
        return talia(kto, "");
    }

    private List<String> talia(String kto, String parametry) throws Exception {
        em.flush();
        em.clear();
        JsonNode t = tresc(zapytaj(kto, "/api/discover/deck?limit=20" + parametry).andExpect(status().isOk()));
        List<String> loginy = new ArrayList<>();
        t.get("cards").forEach(c -> loginy.add(c.get("username").asText()));
        return loginy;
    }

    private JsonNode decyzja(String kto, String kogo, String co) throws Exception {
        return tresc(wyslij(kto, "/api/discover/swipes", Map.of("username", kogo, "decision", co), false)
            .andExpect(status().isOk()));
    }

    private void zasieg(String kto, int km) throws Exception {
        wyslij(kto, "/api/discover/settings", Map.of("enabled", true, "radiusKm", km), true).andExpect(status().isOk());
    }

    /* ---------------------------- wlaczanie ---------------------------- */

    @Test
    @DisplayName("bez wlaczonego trybu nie ma talii ani decyzji (409); zasieg spoza listy - 409; wlaczenie zapisuje sie na koncie")
    void enabling() throws Exception {
        User wylaczony = osoba("dc_off", "Poznań", 52.4064, 16.9252, false);
        em.flush();
        zapytaj("dc_off", "/api/discover/deck").andExpect(status().isConflict());
        wyslij("dc_off", "/api/discover/swipes", Map.of("username", "dc_ja", "decision", "LIKE"), false)
            .andExpect(status().isConflict());
        wyslij("dc_off", "/api/discover/settings", Map.of("enabled", true, "radiusKm", 75), true)
            .andExpect(status().isConflict());

        JsonNode s = tresc(wyslij("dc_off", "/api/discover/settings", Map.of("enabled", true, "radiusKm", 50), true)
            .andExpect(status().isOk()));
        assertThat(s.get("enabled").asBoolean()).isTrue();
        assertThat(s.get("radiusKm").asInt()).isEqualTo(50);
        assertThat(s.get("city").asText()).isEqualTo("Poznań");
        assertThat(s.get("swipesLeft").asInt()).isEqualTo(6);
        assertThat(s.get("preview").get("username").asText()).isEqualTo("dc_off");
        em.flush();
        em.clear();
        assertThat(users.findById(wylaczony.getId()).orElseThrow().isDiscoverEnabled()).isTrue();
    }

    /* ---------------------------- talia ---------------------------- */

    @Test
    @DisplayName("talia: od najlepiej dopasowanych gustem; bez wylaczonych, znajomych, blokad, zaproszen, niepotwierdzonych i z zakazem")
    void whoIsInTheDeckAndInWhatOrder() throws Exception {
        User dwaj = poznan("dc_dwaj");
        dwaj.getFavoriteArtists().add(radiohead);
        dwaj.getFavoriteArtists().add(bjork);
        User jeden = osoba("dc_jeden", "Swarzędz", 52.4108, 17.0794, true);
        jeden.getFavoriteArtists().add(radiohead);
        User nic = poznan("dc_nic");
        nic.getFavoriteArtists().add(kult);

        osoba("dc_wylaczony", "Poznań", 52.4064, 16.9252, false);
        User znajomy = poznan("dc_znajomy");
        ja.addFriend(znajomy);
        User zablokowany = poznan("dc_zablokowany");
        User blokujacy = poznan("dc_blokujacy");
        User zaproszony = poznan("dc_zaproszony");
        requests.save(new FriendRequest(ja, zaproszony));
        User zapraszajacy = poznan("dc_zapraszajacy");
        requests.save(new FriendRequest(zapraszajacy, ja));
        // z miastem - inaczej odpadlby przez zasieg i test nie sprawdzalby potwierdzenia adresu (zywy mutant)
        User niepotwierdzony = new User("dc_niepotw", "dc_niepotw@example.com", "x");
        niepotwierdzony.setCity("Poznań", EventImportService.cityKey("Poznań"), 52.4064, 16.9252);
        niepotwierdzony.setDiscover(true, 100);
        users.save(niepotwierdzony);
        User ukarany = poznan("dc_ukarany");
        ukarany.setBannedUntil(BanKind.POSTING, LocalDateTime.now().plusDays(1));
        User nieczynny = poznan("dc_nieczynny");
        nieczynny.setEnabled(false);
        users.saveAll(List.of(ja, dwaj, jeden, nic, znajomy, ukarany, nieczynny));
        em.flush();
        wyslij("dc_ja", "/api/blocks/dc_zablokowany", Map.of(), true).andExpect(status().isNoContent());
        wyslij("dc_blokujacy", "/api/blocks/dc_ja", Map.of(), true).andExpect(status().isNoContent());

        assertThat(talia("dc_ja")).containsExactly("dc_dwaj", "dc_jeden", "dc_nic");
        // "skip" pomija karty, ktore przegladarka juz ma
        assertThat(talia("dc_ja", "&skip=dc_dwaj&skip=dc_nic")).containsExactly("dc_jeden");
        assertThat(zablokowany.getId()).isNotNull();
        assertThat(blokujacy.getId()).isNotNull();
    }

    @Test
    @DisplayName("karta: zdjecia, opis, pytania, wspolni wykonawcy i gatunki, pasmo odleglosci; miasto ukryte = bez podpisu")
    void cardContents() throws Exception {
        radiohead.getGenres().add("alternative");
        bjork.getGenres().add("art pop");
        artists.saveAll(List.of(radiohead, bjork));
        User bas = osoba("dc_bas", "Swarzędz", 52.4108, 17.0794, true);
        bas.getFavoriteArtists().add(radiohead);
        bas.getFavoriteArtists().add(kult);
        bas.setBio("Basistka, szuka perkusji");
        bas.setLookingFor(java.util.EnumSet.of(com.musicclubapp.entity.LookingFor.JAMMING));
        User skryty = poznan("dc_skryty");
        skryty.getFavoriteArtists().add(kult);
        skryty.setPrivacy(ProfileVisibility.FRIENDS, skryty.getFriendRequestsFrom(), skryty.getClanInvitesFrom(),
            true, true, false, false);
        users.saveAll(List.of(bas, skryty));
        em.flush();

        JsonNode t = tresc(zapytaj("dc_ja", "/api/discover/deck").andExpect(status().isOk()));
        assertThat(t.get("radiusActive").asBoolean()).isTrue();
        JsonNode karta = t.get("cards").get(0);
        assertThat(karta.get("username").asText()).isEqualTo("dc_bas");
        assertThat(karta.get("bio").asText()).isEqualTo("Basistka, szuka perkusji");
        assertThat(karta.get("lookingFor").get(0).asText()).isEqualTo("JAMMING");
        assertThat(karta.get("city").asText()).isEqualTo("Swarzędz");
        assertThat(karta.get("proximity").asText()).isEqualTo("KM_30");
        assertThat(karta.get("sharedArtistCount").asLong()).isEqualTo(1);
        assertThat(karta.get("sharedArtists").get(0).get("name").asText()).isEqualTo("Radiohead");
        assertThat(karta.get("sharedArtists").get(0).get("imageUrl").asText()).isEqualTo("https://img/radiohead.jpg");
        assertThat(karta.get("sharedGenres").toString()).isEqualTo("[\"alternative\"]");
        assertThat(karta.get("tasteLevel").asInt()).isEqualTo(2);           // 5 + 1 = 6 pkt
        assertThat(karta.get("otherArtists").get(0).get("name").asText()).isEqualTo("Kult");
        assertThat(karta.get("newcomer").asBoolean()).isTrue();

        JsonNode ukryta = t.get("cards").get(1);
        assertThat(ukryta.get("username").asText()).isEqualTo("dc_skryty");
        assertThat(ukryta.get("city").isNull()).isTrue();
        assertThat(ukryta.get("proximity").isNull()).isTrue();
        // profil tylko dla znajomych: pozostalych ulubionych na karcie nie ma
        assertThat(ukryta.get("otherArtists")).isEmpty();
        assertThat(ukryta.get("tasteLevel").asInt()).isZero();

        // "Nowa osoba" tylko przez dwa tygodnie od zalozenia konta
        em.createQuery("UPDATE User u SET u.createdAt = :kiedy WHERE u.username = 'dc_skryty'")
            .setParameter("kiedy", LocalDateTime.now().minusDays(15)).executeUpdate();
        em.clear();
        JsonNode po = tresc(zapytaj("dc_ja", "/api/discover/deck").andExpect(status().isOk()));
        assertThat(po.get("cards").get(1).get("username").asText()).isEqualTo("dc_skryty");
        assertThat(po.get("cards").get(1).get("newcomer").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("zasieg: 100 km od Poznania nie siega Warszawy ani osoby bez miasta; 'caly kraj' siega; bez mojego miasta zasieg nic nie robi")
    void radius() throws Exception {
        poznan("dc_blisko");
        osoba("dc_warszawa", "Warszawa", 52.2297, 21.0122, true);
        osoba("dc_bezmiasta", null, null, null, true);
        em.flush();

        assertThat(talia("dc_ja")).containsExactlyInAnyOrder("dc_blisko");
        zasieg("dc_ja", 0);
        assertThat(talia("dc_ja")).containsExactlyInAnyOrder("dc_blisko", "dc_warszawa", "dc_bezmiasta");
        zasieg("dc_ja", 200);
        assertThat(talia("dc_ja")).containsExactlyInAnyOrder("dc_blisko");
        // osoba bez miasta z zasiegiem 30 km widzi wszystkich
        JsonNode t = tresc(zapytaj("dc_bezmiasta", "/api/discover/deck").andExpect(status().isOk()));
        assertThat(t.get("radiusActive").asBoolean()).isFalse();
        assertThat(t.get("cards")).hasSize(3);
    }

    /* ---------------------------- decyzje ---------------------------- */

    @Test
    @DisplayName("wzajemne tak: pierwsze 'tak' jest tajne, drugie robi znajomosc, powiadomienie i push dla obu, decyzje znikaja")
    void mutualLikeMakesFriends() throws Exception {
        poznan("dc_bob");
        em.flush();

        JsonNode pierwsze = decyzja("dc_ja", "dc_bob", "LIKE");
        assertThat(pierwsze.get("matched").asBoolean()).isFalse();
        assertThat(pierwsze.get("swipesLeft").asInt()).isEqualTo(5);
        // bob niczego sie nie dowiaduje: ani powiadomienia, ani innej karty
        assertThat(notifications.countUnread("dc_bob")).isZero();
        assertThat(talia("dc_bob")).containsExactly("dc_ja");
        assertThat(talia("dc_ja")).doesNotContain("dc_bob");

        JsonNode drugie = decyzja("dc_bob", "dc_ja", "LIKE");
        assertThat(drugie.get("matched").asBoolean()).isTrue();
        assertThat(drugie.get("username").asText()).isEqualTo("dc_ja");
        em.flush();
        em.clear();
        assertThat(users.areFriends("dc_ja", "dc_bob")).isTrue();
        assertThat(swipes.count()).isZero();
        for (String kto : List.of("dc_ja", "dc_bob")) {
            JsonNode n = tresc(zapytaj(kto, "/api/notifications").andExpect(status().isOk())).get("content").get(0);
            assertThat(n.get("type").asText()).isEqualTo(NotificationType.DISCOVER_MATCH.name());
            assertThat(n.get("link").asText()).startsWith("/profil/dc_");
        }
        verify(push, atLeastOnce()).send(eq(users.findByUsername("dc_ja").orElseThrow().getId()), any());
        verify(push, atLeastOnce()).send(eq(users.findByUsername("dc_bob").orElseThrow().getId()), any());
        // znajomych juz nie ma w talii, a decyzja o nich to "nie ma jej juz w talii"
        assertThat(talia("dc_ja")).isEmpty();
        wyslij("dc_ja", "/api/discover/swipes", Map.of("username", "dc_bob", "decision", "LIKE"), false)
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("'nie' + 'tak' to nie znajomosc; 'nie' znika z talii, a po 30 dniach osoba wraca")
    void passExpires() throws Exception {
        poznan("dc_bob");
        em.flush();
        decyzja("dc_bob", "dc_ja", "LIKE");
        assertThat(decyzja("dc_ja", "dc_bob", "PASS").get("matched").asBoolean()).isFalse();
        assertThat(users.areFriends("dc_ja", "dc_bob")).isFalse();
        assertThat(talia("dc_ja")).doesNotContain("dc_bob");

        em.createQuery("UPDATE DiscoverSwipe s SET s.createdAt = :kiedy WHERE s.decision = :nie")
            .setParameter("kiedy", LocalDateTime.now().minusDays(31)).setParameter("nie", SwipeDecision.PASS)
            .executeUpdate();
        assertThat(talia("dc_ja")).contains("dc_bob");
        // druga decyzja o tej samej osobie zastepuje stara - teraz "tak", wiec znajomosc
        assertThat(decyzja("dc_ja", "dc_bob", "LIKE").get("matched").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("'tak' bez odpowiedzi nie wygasa po 30 dniach jak 'nie' - ta osoba nie wraca do talii")
    void likeStaysHidden() throws Exception {
        poznan("dc_bob");
        em.flush();
        decyzja("dc_ja", "dc_bob", "LIKE");
        em.createQuery("UPDATE DiscoverSwipe s SET s.createdAt = :kiedy")
            .setParameter("kiedy", LocalDateTime.now().minusDays(40)).executeUpdate();
        assertThat(talia("dc_ja")).doesNotContain("dc_bob");
    }

    @Test
    @DisplayName("cofnij: ostatnia decyzja wraca jako karta; drugi raz - 409; starsza niz 10 minut - 409")
    void undo() throws Exception {
        poznan("dc_bob");
        poznan("dc_cyd");
        // nowsze konto stoi w talii wyzej niz bob - cofniecie ma oddac boba, a nie "pierwsza karte z brzegu"
        poznan("dc_dan");
        em.flush();
        decyzja("dc_ja", "dc_cyd", "LIKE");
        decyzja("dc_ja", "dc_bob", "PASS");

        JsonNode karta = tresc(wyslij("dc_ja", "/api/discover/undo", Map.of(), false).andExpect(status().isOk()));
        assertThat(karta.get("username").asText()).isEqualTo("dc_bob");
        assertThat(talia("dc_ja")).containsExactly("dc_dan", "dc_bob");

        // teraz ostatnia jest "tak" dla cyda - tez da sie cofnac, ale tylko w ciagu 10 minut
        em.createQuery("UPDATE DiscoverSwipe s SET s.createdAt = :kiedy")
            .setParameter("kiedy", LocalDateTime.now().minusMinutes(11)).executeUpdate();
        wyslij("dc_ja", "/api/discover/undo", Map.of(), false).andExpect(status().isConflict());
        assertThat(talia("dc_ja")).containsExactly("dc_dan", "dc_bob");
    }

    @Test
    @DisplayName("nie mozna ocenic siebie, osoby bez trybu ani osoby z blokady - ta sama odpowiedz (409), bez zdradzania powodu")
    void notInDeck() throws Exception {
        osoba("dc_off", "Poznań", 52.4064, 16.9252, false);
        poznan("dc_blok");
        em.flush();
        wyslij("dc_blok", "/api/blocks/dc_ja", Map.of(), true).andExpect(status().isNoContent());

        for (String kogo : List.of("dc_ja", "dc_off", "dc_blok", "dc_nie_ma_takiego")) {
            JsonNode b = tresc(wyslij("dc_ja", "/api/discover/swipes", Map.of("username", kogo, "decision", "LIKE"), false)
                .andExpect(status().isConflict()));
            assertThat(b.get("message").asText()).isEqualTo("Tej osoby nie ma już w talii");
        }
        assertThat(swipes.count()).isZero();
    }

    @Test
    @DisplayName("blokada kasuje decyzje: po odblokowaniu dawne 'tak' nie zamienia sie samo w znajomosc")
    void blockForgetsLikes() throws Exception {
        poznan("dc_bob");
        em.flush();
        decyzja("dc_ja", "dc_bob", "LIKE");
        wyslij("dc_bob", "/api/blocks/dc_ja", Map.of(), true).andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/blocks/dc_ja")
            .with(user("dc_bob")).with(csrf())).andExpect(status().isNoContent());
        em.flush();
        assertThat(swipes.count()).isZero();
        assertThat(decyzja("dc_bob", "dc_ja", "LIKE").get("matched").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("limit dzienny: po szesciu decyzjach 429 z Retry-After do polnocy; licznik w odpowiedzi spada do zera")
    void dailyLimit() throws Exception {
        for (int i = 0; i < 7; i++) {
            poznan("dc_os" + i);
        }
        em.flush();
        for (int i = 0; i < 6; i++) {
            JsonNode d = decyzja("dc_ja", "dc_os" + i, "PASS");
            assertThat(d.get("swipesLeft").asInt()).isEqualTo(5 - i);
        }
        wyslij("dc_ja", "/api/discover/swipes", Map.of("username", "dc_os6", "decision", "PASS"), false)
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        JsonNode t = tresc(zapytaj("dc_ja", "/api/discover/deck").andExpect(status().isOk()));
        assertThat(t.get("swipesLeft").asInt()).isZero();
    }

    @Test
    @DisplayName("sprzatanie: 'nie' starsze niz 30 dni i 'tak' starsze niz 180 znikaja, swiezsze zostaja")
    void cleanup() throws Exception {
        for (String l : List.of("dc_a", "dc_b", "dc_c", "dc_d")) {
            poznan(l);
        }
        em.flush();
        decyzja("dc_ja", "dc_a", "PASS");
        decyzja("dc_ja", "dc_b", "PASS");
        decyzja("dc_ja", "dc_c", "LIKE");
        decyzja("dc_ja", "dc_d", "LIKE");
        postarz("dc_a", 31);
        postarz("dc_b", 29);
        postarz("dc_c", 181);
        postarz("dc_d", 179);
        discover.forgetExpired();
        em.flush();
        em.clear();
        assertThat(swipes.findAll().stream().map(s -> s.getTarget().getUsername()).toList())
            .containsExactlyInAnyOrder("dc_b", "dc_d");
    }

    private void postarz(String kogo, int dni) {
        em.createQuery("UPDATE DiscoverSwipe s SET s.createdAt = :kiedy WHERE s.target.username = :kogo")
            .setParameter("kiedy", LocalDateTime.now().minusDays(dni)).setParameter("kogo", kogo).executeUpdate();
    }

    @Test
    @DisplayName("usuniecie konta zabiera decyzje w obie strony")
    void accountDeletion(@Autowired AccountDeletionService deletion) throws Exception {
        User bob = poznan("dc_bob");
        em.flush();
        decyzja("dc_ja", "dc_bob", "LIKE");
        decyzja("dc_bob", "dc_ja", "PASS");
        em.flush();
        deletion.erase(users.findById(bob.getId()).orElseThrow());
        em.flush();
        assertThat(swipes.count()).isZero();
    }
}
