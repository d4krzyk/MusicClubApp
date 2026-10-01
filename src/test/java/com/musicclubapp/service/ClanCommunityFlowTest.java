package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanJoinRequestRepository;
import com.musicclubapp.repository.ClanMemberTitleRepository;
import com.musicclubapp.repository.ClanPollRepository;
import com.musicclubapp.repository.ClanPollVoteRepository;
import com.musicclubapp.repository.ClanTitleRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Klany jako spolecznosc: prosby o dolaczenie, przegladarka klanow z rekonesansem, wizytowka klanu,
 * tytuly (role), ankiety i ranking aktywnosci - przez prawdziwe API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Klany - spolecznosc")
class ClanCommunityFlowTest {

    private static final String UTWOR = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ClanInvitationRepository invitationRepository;
    @Autowired private ClanJoinRequestRepository requestRepository;
    @Autowired private ClanTitleRepository titleRepository;
    @Autowired private ClanMemberTitleRepository memberTitleRepository;
    @Autowired private ClanPollRepository pollRepository;
    @Autowired private ClanPollVoteRepository pollVoteRepository;
    @Autowired private ArtistRepository artists;
    @Autowired private AccountDeletionService deletion;
    @Autowired private ClanRequestCleanup requestCleanup;
    @Autowired private EntityManager em;

    @MockBean private PushService push;
    @MockBean private MusicMetadataService metadata;

    @BeforeEach
    void setUp() {
        for (String login : List.of("co_ala", "co_bob", "co_cyd", "co_dan", "co_ewa")) {
            users.save(new User(login, login + "@example.com", "x"));
        }
        User szef = new User("co_szef", "co_szef@example.com", "x");
        szef.setRole(Role.ADMIN);
        users.save(szef);
        em.flush();
        given(metadata.fetch(any())).willReturn(new MusicMetadataService.Metadata("Tytul", null));
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions get_(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions wyslij(String metoda, String kto, String adres, Object tresc) throws Exception {
        var builder = switch (metoda) {
            case "PUT" -> put(adres);
            case "DELETE" -> delete(adres);
            default -> post(adres);
        };
        builder.with(user(kto)).with(csrf()).header("Accept-Language", "pl");
        if (tresc != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc));
        }
        return mvc.perform(builder);
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private User nowy(String login) {
        User u = users.save(new User(login, login + "@example.com", "x"));
        em.flush();
        return u;
    }

    /** Zaklada klan; dodatkowe pola (motto, genres, joinPolicy, listed...) wchodza do tresci zadania. */
    private long zaloz(String kto, String nazwa, String skrot, Map<String, Object> dodatkowe) throws Exception {
        Map<String, Object> cialo = new HashMap<>(Map.of("name", nazwa, "tag", skrot));
        cialo.putAll(dodatkowe);
        long id = tresc(wyslij("POST", kto, "/api/clans", cialo).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        return id;
    }

    private long zaloz(String kto, String nazwa, String skrot) throws Exception {
        return zaloz(kto, nazwa, skrot, Map.of());
    }

    private void dolacz(long klan, String zapraszajacy, String kto) throws Exception {
        wyslij("POST", zapraszajacy, "/api/clans/" + klan + "/invitations", Map.of("username", kto)).andExpect(status().isOk());
        em.flush();
        long zaproszenie = invitationRepository.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals(kto)).findFirst().orElseThrow().getId();
        wyslij("POST", kto, "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
        em.flush();
    }

    private JsonNode klan(String kto, long id) throws Exception {
        return tresc(get_(kto, "/api/clans/" + id).andExpect(status().isOk()));
    }

    private void napisz(String kto, long klan, String tekst) throws Exception {
        wyslij("POST", kto, "/api/clans/" + klan + "/chat", Map.of("content", tekst)).andExpect(status().isCreated());
    }

    private List<String> typyPowiadomien(String kto) throws Exception {
        JsonNode strona = tresc(get_(kto, "/api/notifications").andExpect(status().isOk()));
        List<String> typy = new ArrayList<>();
        strona.get("content").forEach(n -> typy.add(n.get("type").asText()));
        return typy;
    }

    private JsonNode katalog(String kto, String zapytanie) throws Exception {
        return tresc(get_(kto, "/api/clans/directory" + zapytanie).andExpect(status().isOk()));
    }

    private List<String> nazwy(JsonNode katalog) {
        List<String> wynik = new ArrayList<>();
        katalog.get("content").forEach(k -> wynik.add(k.get("name").asText()));
        return wynik;
    }

    private void ustawDateZalozenia(long klan, LocalDateTime kiedy) {
        em.createQuery("UPDATE Clan c SET c.createdAt = :d WHERE c.id = :id")
            .setParameter("d", kiedy).setParameter("id", klan).executeUpdate();
        em.clear();
    }

    /* ============================== prosby ============================== */

    @Test
    @DisplayName("prosba: tylko gdy klan je przyjmuje; zarzad dostaje powiadomienie, przyjecie wpuszcza osobe i sprzata")
    void requestFlow() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS");
        // Domyslnie klan przyjmuje tylko zaproszenia (tak jak dotad)
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", Map.of("message", "hej")).andExpect(status().isConflict());
        klan("co_bob", klan);
        get_("co_bob", "/api/clans/" + klan).andExpect(jsonPath("$.canRequest").value(false))
            .andExpect(jsonPath("$.joinPolicy").value("INVITE_ONLY"));

        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("joinPolicy", "REQUESTS")).andExpect(status().isOk());
        get_("co_bob", "/api/clans/" + klan).andExpect(jsonPath("$.canRequest").value(true));
        reset(push);
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", Map.of("message", "  Lubie rock i koncerty  "))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.myRequestStatus").value("PENDING"))
            .andExpect(jsonPath("$.canRequest").value(false));
        em.flush();

        // Zarzad widzi prosbe i dostaje powiadomienie (dzwonek i push); obca osoba prosby nie widzi
        JsonNode dlaAli = klan("co_ala", klan);
        assertThat(dlaAli.get("requests").size()).isEqualTo(1);
        assertThat(dlaAli.get("requests").get(0).get("username").asText()).isEqualTo("co_bob");
        assertThat(dlaAli.get("requests").get(0).get("message").asText()).isEqualTo("Lubie rock i koncerty");
        assertThat(klan("co_cyd", klan).get("requests").size()).isZero();
        assertThat(typyPowiadomien("co_ala")).contains("CLAN_JOIN_REQUEST");
        verify(push, times(1)).send(eq(users.findByUsername("co_ala").orElseThrow().getId()), any(PushService.Message.class));
        // Moje prosby widac na stronie "Klan"
        get_("co_bob", "/api/clans/mine").andExpect(jsonPath("$.requests[0].status").value("PENDING"))
            .andExpect(jsonPath("$.requests[0].clan.name").value("Nocne Sowy"));

        // Drugi raz ta sama prosba, zarzadzanie przez nie-zarzad
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", null).andExpect(status().isConflict());
        long numer = dlaAli.get("requests").get(0).get("id").asLong();
        wyslij("POST", "co_cyd", "/api/clans/" + klan + "/requests/" + numer + "/accept", null).andExpect(status().isConflict());
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests/" + numer + "/accept", null).andExpect(status().isConflict());

        // Cofniecie i ponowna prosba
        wyslij("DELETE", "co_bob", "/api/clans/" + klan + "/requests/mine", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.myRequestStatus").doesNotExist());
        assertThat(typyPowiadomien("co_ala")).doesNotContain("CLAN_JOIN_REQUEST");
        wyslij("DELETE", "co_bob", "/api/clans/" + klan + "/requests/mine", null).andExpect(status().isNotFound());
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", Map.of("message", "jednak chce")).andExpect(status().isCreated());
        em.flush();
        numer = klan("co_ala", klan).get("requests").get(0).get("id").asLong();

        // Przyjecie: bob jest w klanie, jego prosba i dzwonek zarzadu znikaja, a on dostaje powiadomienie
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/requests/" + numer + "/accept", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(2)).andExpect(jsonPath("$.requests.length()").value(0));
        em.flush();
        get_("co_bob", "/api/clans/mine").andExpect(jsonPath("$.clan.myRole").value("MEMBER"));
        assertThat(requestRepository.count()).isZero();
        assertThat(typyPowiadomien("co_bob")).contains("CLAN_REQUEST_ACCEPTED");
        assertThat(typyPowiadomien("co_ala")).doesNotContain("CLAN_JOIN_REQUEST");
        // Ta sama prosba drugi raz - juz nie ma czego przyjmowac
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/requests/" + numer + "/accept", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("prosba odrzucona: bez powiadomienia, tydzien karencji, potem mozna znowu")
    void declinedRequest() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("joinPolicy", "REQUESTS"));
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", null).andExpect(status().isCreated());
        em.flush();
        long numer = klan("co_ala", klan).get("requests").get(0).get("id").asLong();

        wyslij("POST", "co_ala", "/api/clans/" + klan + "/requests/" + numer + "/decline", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.requests.length()").value(0));
        em.flush();
        assertThat(typyPowiadomien("co_bob")).doesNotContain("CLAN_REQUEST_ACCEPTED");
        get_("co_bob", "/api/clans/" + klan).andExpect(jsonPath("$.myRequestStatus").value("DECLINED"))
            .andExpect(jsonPath("$.canRequest").value(false));
        // W karencji - ten sam komunikat co przy klanie, ktory prosb nie przyjmuje; odrzuconej nie da sie "cofnac"
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", null).andExpect(status().isConflict());
        wyslij("DELETE", "co_bob", "/api/clans/" + klan + "/requests/mine", null).andExpect(status().isNotFound());

        // Po tygodniu mozna znowu
        em.createQuery("UPDATE ClanJoinRequest r SET r.answeredAt = :d").setParameter("d", LocalDateTime.now().minusDays(8)).executeUpdate();
        em.clear();
        get_("co_bob", "/api/clans/" + klan).andExpect(jsonPath("$.canRequest").value(true))
            .andExpect(jsonPath("$.myRequestStatus").doesNotExist());
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", Map.of("message", "jeszcze raz")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.myRequestStatus").value("PENDING"));
    }

    @Test
    @DisplayName("prosba: nie z klanu, nie do pelnego, nie gdy jest zaproszenie, najwyzej piec naraz, blokada daje ten sam komunikat")
    void requestLimits() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("joinPolicy", "REQUESTS"));
        long inny = zaloz("co_cyd", "Inny Klan", "IK", Map.of("joinPolicy", "REQUESTS"));

        // Osoba z klanu nie prosi o drugi
        wyslij("POST", "co_cyd", "/api/clans/" + klan + "/requests", null).andExpect(status().isConflict());
        // Zaproszony nie prosi - ma przyjac zaproszenie
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "co_bob")).andExpect(status().isOk());
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", null).andExpect(status().isConflict());
        get_("co_bob", "/api/clans/" + klan).andExpect(jsonPath("$.canRequest").value(false))
            .andExpect(jsonPath("$.invitationId").exists());

        // Blokada z zalozycielem: ten sam wynik co wylaczone prosby, wlasny wpis w stronie klanu nie zdradza przyczyny
        wyslij("PUT", "co_ala", "/api/blocks/co_dan", null).andExpect(status().isNoContent());
        wyslij("POST", "co_dan", "/api/clans/" + klan + "/requests", null).andExpect(status().isConflict());
        get_("co_dan", "/api/clans/" + klan).andExpect(jsonPath("$.canRequest").value(false));

        // Limit pieciu oczekujacych prosb na osobe
        List<Long> klany = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            nowy("co_f" + i);
            klany.add(zaloz("co_f" + i, "Klan Numer " + i, "K" + i, Map.of("joinPolicy", "REQUESTS")));
        }
        for (int i = 0; i < 5; i++) {
            wyslij("POST", "co_ewa", "/api/clans/" + klany.get(i) + "/requests", null).andExpect(status().isCreated());
        }
        wyslij("POST", "co_ewa", "/api/clans/" + klany.get(5) + "/requests", null).andExpect(status().isConflict());
        assertThat(inny).isPositive();

        // Pelny klan nie przyjmuje prosb
        nowy("co_pf");
        long pelny = zaloz("co_pf", "Pelny Klan", "PK", Map.of("joinPolicy", "REQUESTS"));
        for (int i = 0; i < 29; i++) {
            User u = nowy("co_p" + i);
            em.createNativeQuery("INSERT INTO clan_members (clan_id, user_id, role, joined_at, chat_muted) VALUES (?, ?, 'MEMBER', now(), false)")
                .setParameter(1, pelny).setParameter(2, u.getId()).executeUpdate();
        }
        em.clear();
        wyslij("DELETE", "co_ewa", "/api/clans/" + klany.get(0) + "/requests/mine", null).andExpect(status().isOk());
        wyslij("POST", "co_ewa", "/api/clans/" + pelny + "/requests", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("dolaczenie zaproszeniem sprzata inne prosby i zaproszenia tej osoby; zarzad z blokada nie widzi prosby")
    void joiningClearsOtherApplications() throws Exception {
        long a = zaloz("co_ala", "Klan A", "KA", Map.of("joinPolicy", "REQUESTS"));
        long b = zaloz("co_cyd", "Klan B", "KB", Map.of("joinPolicy", "REQUESTS"));
        long c = zaloz("co_ewa", "Klan C", "KC");
        wyslij("POST", "co_bob", "/api/clans/" + a + "/requests", null).andExpect(status().isCreated());
        wyslij("POST", "co_bob", "/api/clans/" + b + "/requests", null).andExpect(status().isCreated());
        assertThat(typyPowiadomien("co_ala")).contains("CLAN_JOIN_REQUEST");

        dolacz(c, "co_ewa", "co_bob");
        em.flush();
        assertThat(requestRepository.count()).isZero();
        assertThat(typyPowiadomien("co_ala")).doesNotContain("CLAN_JOIN_REQUEST");
        assertThat(typyPowiadomien("co_cyd")).doesNotContain("CLAN_JOIN_REQUEST");

        // Zarzad, ktory blokuje proszacego, jego prosby nie widzi ani nie rozpatruje
        wyslij("POST", "co_dan", "/api/clans/" + a + "/requests", null).andExpect(status().isCreated());
        em.flush();
        long numer = klan("co_ala", a).get("requests").get(0).get("id").asLong();
        wyslij("PUT", "co_dan", "/api/blocks/co_ala", null).andExpect(status().isNoContent());
        // Blokada zdjela wszystko miedzy nimi - ale prosba jeszcze jest w bazie: zarzad jej nie widzi
        assertThat(klan("co_ala", a).get("requests").size()).isZero();
        wyslij("POST", "co_ala", "/api/clans/" + a + "/requests/" + numer + "/accept", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sprzatanie: stare nieodebrane prosby i odrzucone po karencji znikaja razem z dzwonkiem zarzadu")
    void requestCleanup() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("joinPolicy", "REQUESTS"));
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/requests", null).andExpect(status().isCreated());
        wyslij("POST", "co_cyd", "/api/clans/" + klan + "/requests", null).andExpect(status().isCreated());
        wyslij("POST", "co_dan", "/api/clans/" + klan + "/requests", null).andExpect(status().isCreated());
        em.flush();
        JsonNode prosby = klan("co_ala", klan).get("requests");
        long odrzucana = 0;
        for (JsonNode p : prosby) {
            if (p.get("username").asText().equals("co_dan")) {
                odrzucana = p.get("id").asLong();
            }
        }
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/requests/" + odrzucana + "/decline", null).andExpect(status().isOk());
        em.flush();

        requestCleanup.clean();
        em.flush();
        assertThat(requestRepository.count()).isEqualTo(3);        // nic jeszcze nie jest stare

        em.createQuery("UPDATE ClanJoinRequest r SET r.createdAt = :d WHERE r.user.id IN (SELECT u.id FROM User u WHERE u.username = 'co_bob')")
            .setParameter("d", LocalDateTime.now().minusDays(31)).executeUpdate();
        em.createQuery("UPDATE ClanJoinRequest r SET r.answeredAt = :d WHERE r.status = com.musicclubapp.entity.InvitationStatus.DECLINED")
            .setParameter("d", LocalDateTime.now().minusDays(8)).executeUpdate();
        em.clear();
        requestCleanup.clean();
        em.flush();
        em.clear();
        assertThat(requestRepository.findAll().stream().map(r -> r.getUser().getUsername()).toList()).containsExactly("co_cyd");
    }

    /* ============================ wizytowka klanu ============================ */

    @Test
    @DisplayName("wizytowka: haslo, miasto, gatunki (male litery, bez powtorzen), nabor i widocznosc; zle dane odrzucone, tylko zarzad zmienia")
    void customization() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("motto", "  Muzyka do poznej nocy ", "city", "Kraków",
            "genres", List.of("Rock", "  INDIE  rock", "rock"), "joinPolicy", "REQUESTS", "listed", false));
        get_("co_ala", "/api/clans/" + klan).andExpect(jsonPath("$.motto").value("Muzyka do poznej nocy"))
            .andExpect(jsonPath("$.city").value("Kraków")).andExpect(jsonPath("$.genres[0]").value("rock"))
            .andExpect(jsonPath("$.genres[1]").value("indie rock")).andExpect(jsonPath("$.genres.length()").value(2))
            .andExpect(jsonPath("$.joinPolicy").value("REQUESTS")).andExpect(jsonPath("$.listed").value(false));

        dolacz(klan, "co_ala", "co_bob");
        // Zwykly czlonek niczego nie zmienia
        wyslij("PUT", "co_bob", "/api/clans/" + klan, Map.of("motto", "moje")).andExpect(status().isConflict());
        wyslij("PUT", "co_dan", "/api/clans/" + klan, Map.of("listed", true)).andExpect(status().isConflict());

        // Zle dane: gatunek ze znakami html, miasto, za duzo gatunkow (walidacja), za dlugie haslo
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("genres", List.of("<b>"))).andExpect(status().isConflict());
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("genres", List.of("a"))).andExpect(status().isConflict());
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("city", "<script>")).andExpect(status().isConflict());
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("genres", List.of("a1", "b2", "c3", "d4"))).andExpect(status().isUnprocessableEntity());
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("motto", "x".repeat(81))).andExpect(status().isUnprocessableEntity());

        // null = bez zmiany, puste = zdjecie; znaki sterujace w hasle znikaja
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("description", "nowy opis")).andExpect(status().isOk())
            .andExpect(jsonPath("$.motto").value("Muzyka do poznej nocy")).andExpect(jsonPath("$.genres.length()").value(2));
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("motto", "Linia\npierwsza\tdruga", "city", "", "genres", List.of(),
            "listed", true)).andExpect(status().isOk()).andExpect(jsonPath("$.motto").value("Linia pierwsza druga"))
            .andExpect(jsonPath("$.city").doesNotExist()).andExpect(jsonPath("$.genres.length()").value(0))
            .andExpect(jsonPath("$.listed").value(true));
    }

    /* ============================ przegladarka klanow ============================ */

    @Test
    @DisplayName("przegladarka: tylko klany z przegladarki, bez klanow z blokada; szukanie, gatunek, miasto, nabor; sortowania i strony")
    void directory() throws Exception {
        long c1 = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("genres", List.of("rock"), "city", "Warszawa",
            "joinPolicy", "REQUESTS", "motto", "Rock do rana"));
        dolacz(c1, "co_ala", "co_bob");
        dolacz(c1, "co_ala", "co_cyd");
        long c2 = zaloz("co_dan", "Jazzowi Wedrowcy", "JW", Map.of("genres", List.of("jazz", "blues"), "city", "Kraków"));
        long c3 = zaloz("co_ewa", "Ukryci", "UK", Map.of("genres", List.of("rock"), "listed", false));
        User blokowany = nowy("co_zly");
        long c4 = zaloz("co_zly", "Zablokowani", "ZB", Map.of("genres", List.of("rock")));
        wyslij("PUT", "co_ala", "/api/blocks/co_zly", null).andExpect(status().isNoContent());
        assertThat(blokowany.getId()).isNotNull();

        // Domyslnie: klany z przegladarki, bez ukrytego i bez takiego, ktorego zalozyciel jest w blokadzie
        JsonNode lista = katalog("co_ala", "?sort=NAME");
        assertThat(nazwy(lista)).containsExactly("Jazzowi Wedrowcy", "Nocne Sowy");
        // Wizytowka bez postow, czatu i osob; z gatunkami, miastem, haslem, liczba osob, naborem
        JsonNode sowy = lista.get("content").get(1);
        assertThat(sowy.get("motto").asText()).isEqualTo("Rock do rana");
        assertThat(sowy.get("city").asText()).isEqualTo("Warszawa");
        assertThat(sowy.get("memberCount").asInt()).isEqualTo(3);
        assertThat(sowy.get("genres").get(0).asText()).isEqualTo("rock");
        assertThat(sowy.get("joinPolicy").asText()).isEqualTo("REQUESTS");
        assertThat(sowy.get("activityLevel").asText()).isEqualTo("NONE");
        assertThat(lista.toString()).doesNotContain("co_ala", "co_bob", "co_cyd", "members", "content\":\"hej");
        // Gatunki do filtra: z calej przegladarki
        assertThat(lista.get("genres").toString()).contains("rock", "jazz", "blues");

        // Szukanie, filtry
        assertThat(nazwy(katalog("co_ewa", "?q=sowy"))).containsExactly("Nocne Sowy");
        assertThat(nazwy(katalog("co_ewa", "?q=NS"))).containsExactly("Nocne Sowy");                  // po skrocie
        assertThat(nazwy(katalog("co_ewa", "?q=do rana"))).containsExactly("Nocne Sowy");           // po hasle
        assertThat(nazwy(katalog("co_ewa", "?genre=Jazz"))).containsExactly("Jazzowi Wedrowcy");
        assertThat(nazwy(katalog("co_ala", "?genre=rock"))).containsExactly("Nocne Sowy");
        // Blokada dziala tylko miedzy tymi osobami: kto jej nie ma, klan "Zablokowani" widzi
        assertThat(nazwy(katalog("co_ewa", "?genre=rock&sort=NAME"))).containsExactly("Nocne Sowy", "Zablokowani");
        assertThat(nazwy(katalog("co_ewa", "?city=krak"))).containsExactly("Jazzowi Wedrowcy");
        nowy("co_fan");
        assertThat(nazwy(katalog("co_fan", "?joinable=true"))).containsExactly("Nocne Sowy");         // Jazzowi Wedrowcy tylko z zaproszenia
    }

    @Test
    @DisplayName("przegladarka: do wziecia tylko dla kogos bez klanu, sortowania i strony")
    void directoryBrowsing() throws Exception {
        nowy("co_gus");
        nowy("co_fan");
        long c1 = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("genres", List.of("rock"), "joinPolicy", "REQUESTS"));
        dolacz(c1, "co_ala", "co_bob");
        dolacz(c1, "co_ala", "co_cyd");
        long c2 = zaloz("co_dan", "Jazzowi Wedrowcy", "JW", Map.of("genres", List.of("jazz"), "joinPolicy", "REQUESTS"));
        dolacz(c2, "co_dan", "co_ewa");
        long c3 = zaloz("co_gus", "Cisi", "CI", Map.of("genres", List.of("ambient")));

        // 'Do wziecia': przyjmuje prosby i nie jest pelny; kto ma klan, nic nie moze wziac
        assertThat(nazwy(katalog("co_fan", "?joinable=true&sort=NAME"))).containsExactly("Jazzowi Wedrowcy", "Nocne Sowy");
        assertThat(nazwy(katalog("co_ala", "?joinable=true"))).isEmpty();
        // Moja prosba i zaproszenie na liscie
        wyslij("POST", "co_fan", "/api/clans/" + c1 + "/requests", null).andExpect(status().isCreated());
        wyslij("POST", "co_gus", "/api/clans/" + c3 + "/invitations", Map.of("username", "co_fan")).andExpect(status().isOk());
        JsonNode dlaFana = katalog("co_fan", "?sort=NAME");
        for (JsonNode k : dlaFana.get("content")) {
            if (k.get("name").asText().equals("Nocne Sowy")) {
                assertThat(k.get("myRequestStatus").asText()).isEqualTo("PENDING");
            }
            if (k.get("name").asText().equals("Cisi")) {
                assertThat(k.get("invited").asBoolean()).isTrue();
            }
        }

        // Sortowania
        assertThat(nazwy(katalog("co_fan", "?sort=MEMBERS"))).first().isEqualTo("Nocne Sowy");   // 3 osoby
        ustawDateZalozenia(c1, LocalDateTime.now().minusDays(30));
        ustawDateZalozenia(c2, LocalDateTime.now().minusDays(10));
        ustawDateZalozenia(c3, LocalDateTime.now().minusDays(1));
        assertThat(nazwy(katalog("co_fan", "?sort=NEWEST"))).containsExactly("Cisi", "Jazzowi Wedrowcy", "Nocne Sowy");
        assertThat(nazwy(katalog("co_fan", "?sort=OLDEST"))).containsExactly("Nocne Sowy", "Jazzowi Wedrowcy", "Cisi");
        assertThat(nazwy(katalog("co_fan", "?sort=NAME"))).containsExactly("Cisi", "Jazzowi Wedrowcy", "Nocne Sowy");
        for (int i = 0; i < 25; i++) {
            napisz("co_dan", c2, "wiadomosc " + i);
        }
        em.flush();
        JsonNode aktywne = katalog("co_fan", "?sort=ACTIVE");
        assertThat(nazwy(aktywne)).first().isEqualTo("Jazzowi Wedrowcy");
        assertThat(aktywne.get("content").get(0).get("activityLevel").asText()).isEqualTo("MEDIUM");

        // Strony
        JsonNode pierwsza = katalog("co_fan", "?sort=NAME&size=2");
        assertThat(pierwsza.get("content").size()).isEqualTo(2);
        assertThat(pierwsza.get("total").asInt()).isEqualTo(3);
        assertThat(pierwsza.get("last").asBoolean()).isFalse();
        JsonNode druga = katalog("co_fan", "?sort=NAME&size=2&page=1");
        assertThat(nazwy(druga)).containsExactly("Nocne Sowy");
        assertThat(druga.get("last").asBoolean()).isTrue();
        assertThat(nazwy(katalog("co_fan", "?sort=NAME&size=2&page=9"))).isEmpty();
        get_("co_fan", "/api/clans/directory?sort=NIEWIADOMO").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("przegladarka: dopasowanie do gustu - wspolni wykonawcy i gatunki, widoczne tylko dla ogladajacego")
    void directoryMatch() throws Exception {
        long rock = zaloz("co_ala", "Rockowcy", "RK", Map.of("genres", List.of("rock")));
        dolacz(rock, "co_ala", "co_bob");
        long jazz = zaloz("co_dan", "Jazzmani", "JZ", Map.of("genres", List.of("jazz")));
        dolacz(jazz, "co_dan", "co_ewa");
        nowy("co_fan");

        Artist a = artists.save(new Artist("co-a", "Artysta A", null));
        a.applyGenres(Set.of("rock", "indie"));
        Artist b = artists.save(new Artist("co-b", "Artysta B", null));
        b.applyGenres(Set.of("jazz"));
        // Dwie osoby z klanu rockowego lubia A (wiec A jest "wspolny" dla klanu), fan tez
        users.findByUsername("co_ala").orElseThrow().getFavoriteArtists().add(a);
        users.findByUsername("co_bob").orElseThrow().getFavoriteArtists().add(a);
        users.findByUsername("co_fan").orElseThrow().getFavoriteArtists().add(a);
        // Jazz: tylko jedna osoba lubi B - to nie jest jeszcze gust klanu
        users.findByUsername("co_dan").orElseThrow().getFavoriteArtists().add(b);
        users.findByUsername("co_fan").orElseThrow().getFavoriteArtists().add(b);
        em.flush();

        JsonNode dlaFana = katalog("co_fan", "?sort=MATCH");
        assertThat(nazwy(dlaFana).get(0)).isEqualTo("Rockowcy");
        JsonNode rockowcy = dlaFana.get("content").get(0);
        assertThat(rockowcy.get("match").asInt()).isGreaterThanOrEqualTo(3);            // wspolny wykonawca + gatunki
        assertThat(rockowcy.get("sharedGenres").toString()).contains("rock");
        assertThat(rockowcy.get("topGenres").toString()).contains("rock", "indie");     // gust zbiorczy klanu
        JsonNode jazzmani = dlaFana.get("content").get(1);
        assertThat(jazzmani.get("match").asInt()).isEqualTo(1);                          // tylko wspolny gatunek (jazz z deklaracji)
        // Kto nie ma ulubionych, nie ma dopasowania; osoba z ograniczonym profilem nie wchodzi do gustu klanu
        JsonNode dlaEwy = katalog("co_cyd", "?sort=MATCH");
        assertThat(dlaEwy.get("content").get(0).get("match").asInt()).isZero();
        users.findByUsername("co_bob").orElseThrow().setPrivacy(com.musicclubapp.entity.ProfileVisibility.FRIENDS,
            com.musicclubapp.entity.InvitePolicy.EVERYONE, com.musicclubapp.entity.ClanInvitePolicy.EVERYONE, true, true, false);
        em.flush();
        JsonNode bezBoba = katalog("co_fan", "?sort=NAME");
        for (JsonNode k : bezBoba.get("content")) {
            if (k.get("name").asText().equals("Rockowcy")) {
                assertThat(k.get("topGenres").size()).isZero();       // zostala jedna osoba z jawnym profilem
                assertThat(k.get("match").asInt()).isEqualTo(1);      // deklarowany gatunek "rock" wciaz sie zgadza
            }
        }
    }

    @Test
    @DisplayName("rekonesans: obcy widzi gust i aktywnosc klanu z przegladarki, ale nigdy czatu ani postow; klan ukryty - tylko swoi i zaproszeni")
    void recon() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("genres", List.of("rock")));
        dolacz(klan, "co_ala", "co_bob");
        Artist a = artists.save(new Artist("co-r", "Artysta R", null));
        a.applyGenres(Set.of("rock"));
        users.findByUsername("co_ala").orElseThrow().getFavoriteArtists().add(a);
        users.findByUsername("co_bob").orElseThrow().getFavoriteArtists().add(a);
        em.flush();
        napisz("co_ala", klan, "tajna rozmowa klanu");

        JsonNode gust = tresc(get_("co_dan", "/api/clans/" + klan + "/taste").andExpect(status().isOk()));
        assertThat(gust.get("artists").get(0).get("name").asText()).isEqualTo("Artysta R");
        assertThat(gust.toString()).doesNotContain("co_ala", "co_bob");
        assertThat(klan("co_dan", klan).get("activityLevel").asText()).isEqualTo("LOW");
        // Ani czat, ani posty, ani ranking, ani ankiety, ani utwory nie sa dla obcych
        get_("co_dan", "/api/clans/" + klan + "/chat").andExpect(status().isConflict());
        get_("co_dan", "/api/posts?clan=" + klan).andExpect(status().isConflict());
        get_("co_dan", "/api/clans/" + klan + "/activity").andExpect(status().isConflict());
        get_("co_dan", "/api/clans/" + klan + "/polls").andExpect(status().isConflict());
        get_("co_dan", "/api/clans/" + klan + "/tracks").andExpect(status().isConflict());
        get_("co_dan", "/api/clans/" + klan + "/events").andExpect(status().isConflict());
        assertThat(klan("co_dan", klan).get("titles").size()).isZero();
        assertThat(klan("co_dan", klan).get("announcement").isNull()).isTrue();

        // Klan ukryty: obcy nie dostaje ani gustu, ani aktywnosci; zaproszony i administrator - tak
        wyslij("PUT", "co_ala", "/api/clans/" + klan, Map.of("listed", false)).andExpect(status().isOk());
        get_("co_dan", "/api/clans/" + klan + "/taste").andExpect(status().isConflict());
        assertThat(klan("co_dan", klan).get("activityLevel").isNull()).isTrue();
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/invitations", Map.of("username", "co_dan")).andExpect(status().isOk());
        get_("co_dan", "/api/clans/" + klan + "/taste").andExpect(status().isOk());
        get_("co_szef", "/api/clans/" + klan + "/taste").andExpect(status().isOk());
    }

    /* ================================ tytuly ================================ */

    private long tytul(String kto, long klan, Map<String, Object> cialo) throws Exception {
        JsonNode odpowiedz = tresc(wyslij("POST", kto, "/api/clans/" + klan + "/titles", cialo).andExpect(status().isCreated()));
        String nazwa = cialo.get("name").toString().strip();
        for (JsonNode t : odpowiedz.get("titles")) {
            if (t.get("name").asText().equalsIgnoreCase(nazwa)) {
                return t.get("id").asLong();
            }
        }
        throw new AssertionError("brak tytulu " + nazwa);
    }

    private List<String> tytulyOsoby(String kto, long klan, String login) throws Exception {
        List<String> wynik = new ArrayList<>();
        for (JsonNode m : klan(kto, klan).get("members")) {
            if (m.get("username").asText().equals(login)) {
                m.get("titles").forEach(t -> wynik.add(t.get("name").asText()));
            }
        }
        return wynik;
    }

    @Test
    @DisplayName("tytuly: zarzad definiuje i nadaje, kazdy bierze te 'do wziecia', automatyczne przychodza z aktywnoscia; limity i uprawnienia")
    void titles() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS");
        dolacz(klan, "co_ala", "co_bob");
        dolacz(klan, "co_ala", "co_cyd");

        long dj = tytul("co_ala", klan, Map.of("name", "DJ", "color", "PINK", "mode", "MANUAL"));
        long gitara = tytul("co_ala", klan, Map.of("name", "Gitarzysta", "color", "TEAL", "mode", "SELF"));
        long gaduly = tytul("co_ala", klan, Map.of("name", "Gaduła", "color", "ORANGE", "mode", "AUTO",
            "metric", "MESSAGES", "threshold", 3));

        // Uprawnienia i zle dane
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/titles", Map.of("name", "Bob", "color", "RED", "mode", "SELF")).andExpect(status().isConflict());
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/titles", Map.of("name", "dj", "color", "RED", "mode", "SELF")).andExpect(status().isConflict());           // ten sam klucz
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/titles", Map.of("name", "<b>", "color", "RED", "mode", "SELF")).andExpect(status().isConflict());
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/titles", Map.of("name", "Auto", "color", "RED", "mode", "AUTO")).andExpect(status().isConflict());           // bez progu
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/titles", Map.of("name", "Auto", "color", "RED", "mode", "AUTO", "metric", "POSTS", "threshold", 0)).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/titles", Map.of("name", "Zly", "color", "NIE", "mode", "SELF")).andExpect(status().isBadRequest());

        // Nadawanie przez zarzad; automatycznego nie da sie nadac; tylko zarzad nadaje
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/members/co_cyd/titles/" + dj, null).andExpect(status().isConflict());
        wyslij("PUT", "co_ala", "/api/clans/" + klan + "/members/co_cyd/titles/" + gaduly, null).andExpect(status().isConflict());
        wyslij("PUT", "co_ala", "/api/clans/" + klan + "/members/co_dan/titles/" + dj, null).andExpect(status().isNotFound());     // nie w klanie
        wyslij("PUT", "co_ala", "/api/clans/" + klan + "/members/co_cyd/titles/" + dj, null).andExpect(status().isOk());
        wyslij("PUT", "co_ala", "/api/clans/" + klan + "/members/co_cyd/titles/" + dj, null).andExpect(status().isOk());           // drugi raz: bez zmian
        assertThat(tytulyOsoby("co_bob", klan, "co_cyd")).containsExactly("DJ");
        assertThat(memberTitleRepository.count()).isEqualTo(1);

        // Branie samemu: tylko SELF, najwyzej dwa; oddawanie
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/titles/" + dj + "/claim", null).andExpect(status().isConflict());
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/titles/" + gitara + "/claim", null).andExpect(status().isOk());
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).containsExactly("Gitarzysta");
        JsonNode definicje = klan("co_bob", klan).get("titles");
        for (JsonNode t : definicje) {
            if (t.get("id").asLong() == gitara) {
                assertThat(t.get("mine").asBoolean()).isTrue();
                assertThat(t.get("canClaim").asBoolean()).isFalse();
                assertThat(t.get("holders").asInt()).isEqualTo(1);
            }
        }
        long s2 = tytul("co_ala", klan, Map.of("name", "Perkusista", "color", "BLUE", "mode", "SELF"));
        long s3 = tytul("co_ala", klan, Map.of("name", "Basista", "color", "GREEN", "mode", "SELF"));
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/titles/" + s2 + "/claim", null).andExpect(status().isOk());
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/titles/" + s3 + "/claim", null).andExpect(status().isConflict());   // trzeci
        wyslij("DELETE", "co_bob", "/api/clans/" + klan + "/titles/" + s2 + "/claim", null).andExpect(status().isOk());
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/titles/" + s3 + "/claim", null).andExpect(status().isOk());
        // Cudzy tytul 'do wziecia' zdejmuje zarzad, a nie inny czlonek; wlasny tytul nadany recznie - tylko zarzad
        wyslij("DELETE", "co_cyd", "/api/clans/" + klan + "/members/co_bob/titles/" + gitara, null).andExpect(status().isConflict());
        wyslij("DELETE", "co_bob", "/api/clans/" + klan + "/members/co_cyd/titles/" + dj, null).andExpect(status().isConflict());
        wyslij("DELETE", "co_ala", "/api/clans/" + klan + "/members/co_bob/titles/" + s3, null).andExpect(status().isOk());

        // Automatyczny: bob ma 0 wiadomosci - brak; po trzech jest
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).doesNotContain("Gaduła");
        napisz("co_bob", klan, "raz");
        napisz("co_bob", klan, "dwa");
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).doesNotContain("Gaduła");
        napisz("co_bob", klan, "trzy");
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).contains("Gaduła", "Gitarzysta");
        assertThat(tytulyOsoby("co_ala", klan, "co_cyd")).doesNotContain("Gaduła");
        assertThat(memberTitleRepository.count()).isEqualTo(2);       // automatyczny nie ma wiersza

        // Obcy nie widzi tytulow; odejscie z klanu zabiera tytuly
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).isNotEmpty();
        wyslij("DELETE", "co_bob", "/api/clans/" + klan + "/members/me", null).andExpect(status().isOk());
        em.flush();
        assertThat(memberTitleRepository.count()).isEqualTo(1);       // zostal tylko DJ cyda
        dolacz(klan, "co_ala", "co_bob");
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).containsExactly("Gaduła").doesNotContain("Gitarzysta");   // 3 wiadomosci wciaz sa

        // Zmiana trybu na automatyczny zdejmuje nadane; usuniecie tytulu - jego posiadaczy
        wyslij("PUT", "co_ala", "/api/clans/" + klan + "/titles/" + dj, Map.of("name", "DJ", "color", "PINK", "mode", "AUTO",
            "metric", "DAYS", "threshold", 1)).andExpect(status().isOk());
        assertThat(memberTitleRepository.count()).isZero();
        wyslij("DELETE", "co_ala", "/api/clans/" + klan + "/titles/" + gaduly, null).andExpect(status().isOk());
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).doesNotContain("Gaduła");
        assertThat(titleRepository.count()).isEqualTo(4);   // DJ, Gitarzysta, Perkusista, Basista
    }

    @Test
    @DisplayName("tytuly: najwyzej dwanascie w klanie, pieciu nadanych na osobe; metryka DAYS liczy staz")
    void titleLimits() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS");
        dolacz(klan, "co_ala", "co_bob");
        List<Long> id = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            id.add(tytul("co_ala", klan, Map.of("name", "Rola " + i, "color", "BLUE", "mode", "MANUAL")));
        }
        wyslij("POST", "co_ala", "/api/clans/" + klan + "/titles", Map.of("name", "Trzynasta", "color", "BLUE", "mode", "MANUAL"))
            .andExpect(status().isConflict());
        for (int i = 0; i < 5; i++) {
            wyslij("PUT", "co_ala", "/api/clans/" + klan + "/members/co_bob/titles/" + id.get(i), null).andExpect(status().isOk());
        }
        wyslij("PUT", "co_ala", "/api/clans/" + klan + "/members/co_bob/titles/" + id.get(5), null).andExpect(status().isConflict());

        // Staz: bob jest w klanie od dawna
        em.createQuery("UPDATE ClanMember m SET m.joinedAt = :d WHERE m.user.id IN (SELECT u.id FROM User u WHERE u.username = 'co_bob')")
            .setParameter("d", LocalDateTime.now().minusDays(100)).executeUpdate();
        em.clear();
        wyslij("DELETE", "co_ala", "/api/clans/" + klan + "/titles/" + id.get(11), null).andExpect(status().isOk());
        long weteran = tytul("co_ala", klan, Map.of("name", "Weteran", "color", "SLATE", "mode", "AUTO", "metric", "DAYS", "threshold", 90));
        assertThat(tytulyOsoby("co_ala", klan, "co_bob")).contains("Weteran");
        assertThat(tytulyOsoby("co_ala", klan, "co_ala")).doesNotContain("Weteran");
        assertThat(weteran).isPositive();
    }

    /* ================================ ankiety ================================ */

    private long ankieta(String kto, long klan, Object cialo) throws Exception {
        return tresc(wyslij("POST", kto, "/api/clans/" + klan + "/polls", cialo).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode ankiety(String kto, long klan) throws Exception {
        return tresc(get_(kto, "/api/clans/" + klan + "/polls").andExpect(status().isOk()));
    }

    @Test
    @DisplayName("ankiety: dwie do szesciu odpowiedzi, jeden glos do zmiany, wyniki bez nazwisk; zamykanie i terminy; tylko czlonkowie")
    void polls() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS");
        dolacz(klan, "co_ala", "co_bob");
        dolacz(klan, "co_ala", "co_cyd");
        String adres = "/api/clans/" + klan + "/polls";

        // Zle ankiety
        wyslij("POST", "co_ala", adres, Map.of("question", "Pytanie", "options", List.of("jedna"))).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_ala", adres, Map.of("question", "Pytanie", "options", List.of("a", "A"))).andExpect(status().isConflict());
        wyslij("POST", "co_ala", adres, Map.of("question", "Pytanie", "options", List.of("a", " "))).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_ala", adres, Map.of("question", "Pytanie", "options", List.of("a", "b", "c", "d", "e", "f", "g"))).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_ala", adres, Map.of("question", "Pytanie", "options", List.of("a", "b"), "days", 5)).andExpect(status().isConflict());
        wyslij("POST", "co_ala", adres, Map.of("question", " ", "options", List.of("a", "b"))).andExpect(status().isUnprocessableEntity());
        wyslij("POST", "co_dan", adres, Map.of("question", "Pytanie", "options", List.of("a", "b"))).andExpect(status().isConflict());   // obcy

        long ankieta = ankieta("co_ala", klan, Map.of("question", "Jaki koncert wybieramy?", "options", List.of("Jesienny", "Zimowy", "Wiosenny"), "days", 3));
        JsonNode odpowiedzi = ankiety("co_bob", klan).get(0).get("options");
        long jesienny = odpowiedzi.get(0).get("id").asLong();
        long zimowy = odpowiedzi.get(1).get("id").asLong();

        wyslij("PUT", "co_bob", adres + "/" + ankieta + "/vote", Map.of("optionId", jesienny)).andExpect(status().isNoContent());
        wyslij("PUT", "co_cyd", adres + "/" + ankieta + "/vote", Map.of("optionId", jesienny)).andExpect(status().isNoContent());
        wyslij("PUT", "co_ala", adres + "/" + ankieta + "/vote", Map.of("optionId", zimowy)).andExpect(status().isNoContent());
        // Zmiana glosu podmienia, a nie dodaje
        wyslij("PUT", "co_cyd", adres + "/" + ankieta + "/vote", Map.of("optionId", zimowy)).andExpect(status().isNoContent());
        assertThat(pollVoteRepository.count()).isEqualTo(3);
        JsonNode wynik = ankiety("co_bob", klan).get(0);
        assertThat(wynik.get("totalVotes").asInt()).isEqualTo(3);
        assertThat(wynik.get("myOption").asLong()).isEqualTo(jesienny);
        assertThat(wynik.get("options").get(0).get("votes").asInt()).isEqualTo(1);
        assertThat(wynik.get("options").get(1).get("votes").asInt()).isEqualTo(2);
        assertThat(wynik.get("open").asBoolean()).isTrue();
        assertThat(wynik.toString()).doesNotContain("co_cyd");                            // nie widac, kto na co
        // Odpowiedz z innej ankiety, obcy, cofniecie
        wyslij("PUT", "co_bob", adres + "/" + ankieta + "/vote", Map.of("optionId", 99999999L)).andExpect(status().isNotFound());
        wyslij("PUT", "co_dan", adres + "/" + ankieta + "/vote", Map.of("optionId", jesienny)).andExpect(status().isConflict());
        get_("co_dan", adres).andExpect(status().isConflict());
        wyslij("DELETE", "co_bob", adres + "/" + ankieta + "/vote", null).andExpect(status().isNoContent());
        assertThat(ankiety("co_bob", klan).get(0).get("myOption").isNull()).isTrue();

        // Zamykanie: autor albo zarzad, nie inny czlonek; po zamknieciu glosowania nie ma
        wyslij("POST", "co_cyd", adres + "/" + ankieta + "/close", null).andExpect(status().isConflict());
        wyslij("POST", "co_ala", adres + "/" + ankieta + "/close", null).andExpect(status().isNoContent());
        wyslij("PUT", "co_bob", adres + "/" + ankieta + "/vote", Map.of("optionId", jesienny)).andExpect(status().isConflict());
        wyslij("DELETE", "co_cyd", adres + "/" + ankieta + "/vote", null).andExpect(status().isConflict());
        assertThat(ankiety("co_bob", klan).get(0).get("open").asBoolean()).isFalse();
        assertThat(ankiety("co_bob", klan).get(0).get("totalVotes").asInt()).isEqualTo(2);   // wyniki zostaja

        // Skasowanie: nie przez kogos obcego z klanu
        wyslij("DELETE", "co_cyd", adres + "/" + ankieta, null).andExpect(status().isConflict());
        wyslij("DELETE", "co_ala", adres + "/" + ankieta, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(pollRepository.count()).isZero();
        assertThat(pollVoteRepository.count()).isZero();
    }

    @Test
    @DisplayName("ankiety: minuty zamykaja same, limit dwoch otwartych na osobe i pieciu w klanie, ankiety i glosy osob z blokad ukryte")
    void pollLimitsAndBlocks() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS");
        dolacz(klan, "co_ala", "co_bob");
        dolacz(klan, "co_ala", "co_cyd");
        dolacz(klan, "co_ala", "co_ewa");
        String adres = "/api/clans/" + klan + "/polls";
        Map<String, Object> cialo = Map.of("question", "Pytanie?", "options", List.of("tak", "nie"));

        long pierwsza = ankieta("co_bob", klan, cialo);
        ankieta("co_bob", klan, cialo);
        wyslij("POST", "co_bob", adres, cialo).andExpect(status().isConflict());           // trzecia osoby
        ankieta("co_cyd", klan, cialo);
        ankieta("co_cyd", klan, cialo);
        ankieta("co_ewa", klan, cialo);
        wyslij("POST", "co_ala", adres, cialo).andExpect(status().isConflict());           // szosta w klanie

        // Termin minal - zamknieta sama; mozna zalozyc nastepna
        em.createQuery("UPDATE ClanPoll p SET p.closesAt = :d WHERE p.id = :id")
            .setParameter("d", LocalDateTime.now().minusMinutes(1)).setParameter("id", pierwsza).executeUpdate();
        em.clear();
        JsonNode odpowiedz = ankiety("co_ala", klan);
        long zamknietych = 0;
        for (JsonNode a : odpowiedz) {
            if (!a.get("open").asBoolean()) {
                zamknietych++;
                assertThat(a.get("id").asLong()).isEqualTo(pierwsza);
            }
        }
        assertThat(zamknietych).isEqualTo(1);
        assertThat(odpowiedz.get(odpowiedz.size() - 1).get("open").asBoolean()).isFalse();        // zamknieta na koncu listy
        wyslij("PUT", "co_ala", adres + "/" + pierwsza + "/vote",
            Map.of("optionId", ankiety("co_ala", klan).get(odpowiedz.size() - 1).get("options").get(0).get("id").asLong())).andExpect(status().isConflict());
        ankieta("co_bob", klan, cialo);                                                    // miejsce sie zwolnilo

        // Blokada: ankiety blokowanego autora i jego glosy znikaja z widoku
        wyslij("PUT", "co_ala", "/api/blocks/co_cyd", null).andExpect(status().isNoContent());
        for (JsonNode a : ankiety("co_ala", klan)) {
            assertThat(a.get("authorUsername").asText()).isNotEqualTo("co_cyd");
        }
        wyslij("PUT", "co_ewa", adres + "/" + ankiety("co_ewa", klan).get(0).get("id").asLong() + "/vote",
            Map.of("optionId", ankiety("co_ewa", klan).get(0).get("options").get(0).get("id").asLong())).andExpect(status().isNoContent());
    }

    /* ================================ ranking ================================ */

    @Test
    @DisplayName("ranking: punkty z czatu, postow, propozycji, glosow i reakcji; tydzien a caly czas; poziomy; cel tygodnia; blokady")
    void activity() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS");
        dolacz(klan, "co_ala", "co_bob");
        dolacz(klan, "co_ala", "co_cyd");

        for (int i = 0; i < 3; i++) {
            napisz("co_bob", klan, "wiadomosc " + i);
        }
        napisz("co_ala", klan, "jedna");
        MockMultipartFile czesc = new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE,
            json.writeValueAsBytes(Map.of("content", "post klanu", "clanId", klan)));
        mvc.perform(multipart("/api/posts").file(czesc).with(user("co_bob")).with(csrf())).andExpect(status().isCreated());
        wyslij("POST", "co_bob", "/api/clans/" + klan + "/tracks", Map.of("url", UTWOR)).andExpect(status().isCreated());
        long utwor = tresc(get_("co_bob", "/api/clans/" + klan + "/tracks")).get("tracks").get(0).get("id").asLong();
        wyslij("PUT", "co_cyd", "/api/clans/" + klan + "/tracks/" + utwor + "/vote", null).andExpect(status().isNoContent());
        long wiadomosc = tresc(get_("co_bob", "/api/clans/" + klan + "/chat")).get(0).get("id").asLong();
        wyslij("PUT", "co_cyd", "/api/clans/" + klan + "/chat/" + wiadomosc + "/reaction", Map.of("emoji", "FIRE")).andExpect(status().isOk());
        em.flush();

        // bob: 3 wiadomosci (3) + post (4) + propozycja (3) = 10; cyd: glos (1) + reakcja (1) = 2; ala: 1
        JsonNode tydzien = tresc(get_("co_ala", "/api/clans/" + klan + "/activity").andExpect(status().isOk()));
        assertThat(tydzien.get("period").asText()).isEqualTo("WEEK");
        assertThat(tydzien.get("ranking").get(0).get("username").asText()).isEqualTo("co_bob");
        assertThat(tydzien.get("ranking").get(0).get("points").asInt()).isEqualTo(10);
        assertThat(tydzien.get("ranking").get(0).get("messages").asInt()).isEqualTo(3);
        assertThat(tydzien.get("ranking").get(0).get("posts").asInt()).isEqualTo(1);
        assertThat(tydzien.get("ranking").get(0).get("tracks").asInt()).isEqualTo(1);
        assertThat(tydzien.get("ranking").get(1).get("username").asText()).isEqualTo("co_cyd");
        assertThat(tydzien.get("ranking").get(1).get("points").asInt()).isEqualTo(2);
        assertThat(tydzien.get("ranking").get(2).get("points").asInt()).isEqualTo(1);
        assertThat(tydzien.get("summary").get("points").asInt()).isEqualTo(13);
        assertThat(tydzien.get("summary").get("goal").asInt()).isEqualTo(60);              // 20 punktow na osobe, 3 osoby
        assertThat(tydzien.get("ranking").get(0).get("level").asInt()).isZero();
        assertThat(tydzien.get("ranking").get(0).get("nextLevelAt").asInt()).isEqualTo(25);
        assertThat(tydzien.get("ranking").get(2).get("me").asBoolean()).isTrue();

        // Stare wiadomosci: poza tygodniem, ale w calym czasie; poziom z calego czasu (25 punktow = poziom 1)
        em.createQuery("UPDATE ClanMessage m SET m.createdAt = :d").setParameter("d", LocalDateTime.now().minusDays(10)).executeUpdate();
        em.clear();
        JsonNode bezStarych = tresc(get_("co_ala", "/api/clans/" + klan + "/activity?period=WEEK"));
        assertThat(bezStarych.get("ranking").get(0).get("points").asInt()).isEqualTo(7);   // post + propozycja
        JsonNode calosc = tresc(get_("co_ala", "/api/clans/" + klan + "/activity?period=ALL"));
        assertThat(calosc.get("period").asText()).isEqualTo("ALL");
        assertThat(calosc.get("ranking").get(0).get("points").asInt()).isEqualTo(10);
        for (int i = 0; i < 15; i++) {
            napisz("co_bob", klan, "dodatkowa " + i);
        }
        em.flush();
        JsonNode poziom = tresc(get_("co_bob", "/api/clans/" + klan + "/activity?period=ALL"));
        assertThat(poziom.get("ranking").get(0).get("allTimePoints").asInt()).isEqualTo(25);
        assertThat(poziom.get("ranking").get(0).get("level").asInt()).isEqualTo(1);
        assertThat(poziom.get("ranking").get(0).get("nextLevelAt").asInt()).isEqualTo(100);

        // Blokada: osoba z blokady nie wystepuje w rankingu ogladajacego; obcy nie widzi, administrator tak
        wyslij("PUT", "co_cyd", "/api/blocks/co_bob", null).andExpect(status().isNoContent());
        JsonNode dlaCyda = tresc(get_("co_cyd", "/api/clans/" + klan + "/activity?period=ALL"));
        for (JsonNode w : dlaCyda.get("ranking")) {
            assertThat(w.get("username").asText()).isNotEqualTo("co_bob");
        }
        get_("co_dan", "/api/clans/" + klan + "/activity").andExpect(status().isConflict());
        get_("co_szef", "/api/clans/" + klan + "/activity").andExpect(status().isOk());
    }

    /* ================================ sprzatanie ================================ */

    @Test
    @DisplayName("rozwiazanie klanu i usuniecie konta zabieraja prosby, tytuly, ankiety i glosy bez bledow kluczy obcych")
    void cleanup() throws Exception {
        long klan = zaloz("co_ala", "Nocne Sowy", "NS", Map.of("joinPolicy", "REQUESTS", "genres", List.of("rock", "jazz")));
        dolacz(klan, "co_ala", "co_bob");
        dolacz(klan, "co_ala", "co_cyd");
        wyslij("POST", "co_dan", "/api/clans/" + klan + "/requests", Map.of("message", "prosze")).andExpect(status().isCreated());
        long gitara = tytul("co_ala", klan, Map.of("name", "Gitarzysta", "color", "TEAL", "mode", "SELF"));
        wyslij("PUT", "co_bob", "/api/clans/" + klan + "/titles/" + gitara + "/claim", null).andExpect(status().isOk());
        long ankieta = ankieta("co_bob", klan, Map.of("question", "Pytanie?", "options", List.of("tak", "nie")));
        long opcja = ankiety("co_cyd", klan).get(0).get("options").get(0).get("id").asLong();
        wyslij("PUT", "co_cyd", "/api/clans/" + klan + "/polls/" + ankieta + "/vote", Map.of("optionId", opcja)).andExpect(status().isNoContent());
        long drugaAnkieta = ankieta("co_cyd", klan, Map.of("question", "Druga?", "options", List.of("a", "b")));
        em.flush();
        // Prosba dana przez cyda do innego klanu
        long inny = zaloz("co_ewa", "Inny Klan", "IK", Map.of("joinPolicy", "REQUESTS"));
        assertThat(inny).isPositive();

        // Usuniecie konta autora ankiety i posiadacza tytulu: znikaja jego ankieta (z cudzymi glosami) i tytul
        em.clear();
        deletion.erase(users.findByUsername("co_bob").orElseThrow());
        em.flush();
        em.clear();
        assertThat(pollRepository.count()).isEqualTo(1);              // zostala ankieta cyda
        assertThat(pollVoteRepository.count()).isZero();              // glos cyda byl pod ankieta boba
        assertThat(memberTitleRepository.count()).isZero();
        assertThat(titleRepository.count()).isEqualTo(1);              // sama definicja tytulu zostaje w klanie

        // Usuniecie konta osoby, ktora prosila o dolaczenie
        deletion.erase(users.findByUsername("co_dan").orElseThrow());
        em.flush();
        em.clear();
        assertThat(requestRepository.count()).isZero();

        // Rozwiazanie klanu z tym, co w nim zostalo, i z prosba innej osoby
        wyslij("POST", "co_ewa", "/api/clans/" + klan + "/requests", null).andExpect(status().isConflict());   // ewa ma klan
        nowy("co_gosc");
        wyslij("POST", "co_gosc", "/api/clans/" + klan + "/requests", null).andExpect(status().isCreated());
        em.flush();
        wyslij("DELETE", "co_ala", "/api/clans/" + klan, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(pollRepository.count()).isZero();
        assertThat(titleRepository.count()).isZero();
        assertThat(requestRepository.count()).isZero();
        assertThat(((Number) em.createNativeQuery("SELECT COUNT(*) FROM clan_genres").getSingleResult()).longValue()).isZero();
        assertThat(drugaAnkieta).isPositive();
    }
}
