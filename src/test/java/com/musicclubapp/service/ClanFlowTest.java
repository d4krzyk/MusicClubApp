package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.ClanInvitePolicy;
import com.musicclubapp.entity.InvitePolicy;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Klany przez prawdziwe API: zakladanie, zaproszenia jako jedyna droga do klanu, role,
 * kolor z glosowania, posty i czat widoczne tylko dla czlonkow (i administratora aplikacji),
 * rozwiazanie klanu i usuniecie konta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Klany - caly przebieg")
class ClanFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ClanRepository clanRepository;
    @Autowired private ClanMemberRepository memberRepository;
    @Autowired private ClanInvitationRepository invitationRepository;
    @Autowired private ClanMessageRepository messageRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private AccountDeletionService deletion;
    @Autowired private EntityManager em;

    private User ala;
    private User bob;
    private User cyd;
    private User dan;
    private User ewa;
    private User szef;

    @BeforeEach
    void setUp() {
        ala = users.save(new User("cl_ala", "cl_ala@example.com", "x"));
        bob = users.save(new User("cl_bob", "cl_bob@example.com", "x"));
        cyd = users.save(new User("cl_cyd", "cl_cyd@example.com", "x"));
        dan = users.save(new User("cl_dan", "cl_dan@example.com", "x"));
        ewa = users.save(new User("cl_ewa", "cl_ewa@example.com", "x"));
        szef = new User("cl_szef", "cl_szef@example.com", "x");
        szef.setRole(Role.ADMIN);
        users.save(szef);
        em.flush();
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

    private long zaloz(String kto, String nazwa, String skrot) throws Exception {
        String odpowiedz = wyslij("POST", kto, "/api/clans", Map.of("name", nazwa, "tag", skrot, "description", "opis"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        em.flush();
        return json.readTree(odpowiedz).get("id").asLong();
    }

    /** Zaprasza i od razu przyjmuje - dla kogos, kto ma byc czlonkiem. */
    private void dolacz(long klan, String zapraszajacy, String kto) throws Exception {
        wyslij("POST", zapraszajacy, "/api/clans/" + klan + "/invitations", Map.of("username", kto))
            .andExpect(status().isOk());
        em.flush();
        long zaproszenie = invitationRepository.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals(kto)).findFirst().orElseThrow().getId();
        wyslij("POST", kto, "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isOk());
        em.flush();
    }

    private long zaproszenieDla(String kto) {
        return invitationRepository.findAll().stream()
            .filter(i -> i.getInvitee().getUsername().equals(kto)).findFirst().orElseThrow().getId();
    }

    private ResultActions napiszPost(String kto, long klan, String tresc) throws Exception {
        Map<String, Object> pola = new HashMap<>();
        pola.put("content", tresc);
        pola.put("clanId", klan);
        MockMultipartFile czesc = new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE,
            json.writeValueAsBytes(pola));
        return mvc.perform(multipart("/api/posts").file(czesc).with(user(kto)).with(csrf()).header("Accept-Language", "pl"));
    }

    private List<String> tresci(String kto, String adres) throws Exception {
        JsonNode strona = json.readTree(get_(kto, adres).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        List<String> wynik = new ArrayList<>();
        strona.get("content").forEach(p -> wynik.add(p.get("content").asText()));
        return wynik;
    }

    /* ------------------------------- testy ------------------------------- */

    @Test
    @DisplayName("zakladanie: nazwa i skrot sprawdzane, unikalne bez wzgledu na litery i znaki, jeden klan na osobe")
    void createRules() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "ns");
        get_("cl_ala", "/api/clans/" + id)
            .andExpect(jsonPath("$.tag").value("NS"))
            .andExpect(jsonPath("$.myRole").value("FOUNDER"))
            .andExpect(jsonPath("$.color").value("VIOLET"))
            .andExpect(jsonPath("$.memberCount").value(1));

        // Ta sama nazwa inaczej zapisana, ten sam skrot
        wyslij("POST", "cl_bob", "/api/clans", Map.of("name", "NOCNE  sowy", "tag", "XY")).andExpect(status().isConflict());
        wyslij("POST", "cl_bob", "/api/clans", Map.of("name", "Inny", "tag", "NS")).andExpect(status().isConflict());
        wyslij("POST", "cl_bob", "/api/clans", Map.of("name", "Zażółć Gęślą", "tag", "ZG")).andExpect(status().isCreated());
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "Zazolc Gesla", "tag", "ZZ")).andExpect(status().isConflict());
        // Zla dlugosc, znaki, udawanie aplikacji
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "ab", "tag", "AB")).andExpect(status().isConflict());
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "<b>x</b>", "tag", "AB")).andExpect(status().isConflict());
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "MusicClub Oficjalny", "tag", "AB")).andExpect(status().isConflict());
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "Admin team", "tag", "AB")).andExpect(status().isConflict());
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "Dobra nazwa", "tag", "A")).andExpect(status().isConflict());
        wyslij("POST", "cl_cyd", "/api/clans", Map.of("name", "Dobra nazwa", "tag", "AB CD")).andExpect(status().isConflict());
        // Jeden klan na osobe
        wyslij("POST", "cl_ala", "/api/clans", Map.of("name", "Drugi klan", "tag", "DK")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("do klanu wchodzi sie tylko przez zaproszenie od czlonka; obcy nie widzi czatu ani postow")
    void inviteOnly() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");

        // Bez zaproszenia: ani czat, ani posty, ani pisanie
        get_("cl_bob", "/api/clans/" + id + "/chat").andExpect(status().isConflict());
        get_("cl_bob", "/api/posts?clan=" + id).andExpect(status().isConflict());
        napiszPost("cl_bob", id, "wchodze bez pytania").andExpect(status().isConflict());
        wyslij("POST", "cl_bob", "/api/clans/" + id + "/chat", Map.of("content", "hej")).andExpect(status().isConflict());
        // Obcy nie moze zapraszac (ani samego siebie)
        wyslij("POST", "cl_bob", "/api/clans/" + id + "/invitations", Map.of("username", "cl_cyd")).andExpect(status().isConflict());
        wyslij("POST", "cl_bob", "/api/clans/" + id + "/invitations", Map.of("username", "cl_bob")).andExpect(status().isConflict());
        // Strona klanu jest jawna, ale bez zawartosci
        get_("cl_bob", "/api/clans/" + id).andExpect(status().isOk())
            .andExpect(jsonPath("$.canSeeContent").value(false)).andExpect(jsonPath("$.myRole").doesNotExist())
            .andExpect(jsonPath("$.invitations.length()").value(0));

        // Zaproszenie: powiadomienie w dzwonku, zaproszony widzi je w "mine"
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_bob")).andExpect(status().isOk());
        em.flush();
        get_("cl_bob", "/api/notifications").andExpect(jsonPath("$.content[0].type").value("CLAN_INVITE"))
            .andExpect(jsonPath("$.content[0].clanName").value("Nocne Sowy"))
            .andExpect(jsonPath("$.content[0].link").value("/klan"));
        get_("cl_bob", "/api/clans/mine").andExpect(jsonPath("$.clan").doesNotExist())
            .andExpect(jsonPath("$.invitations[0].clan.name").value("Nocne Sowy"))
            .andExpect(jsonPath("$.invitations[0].inviterUsername").value("cl_ala"));
        // Drugi raz to samo zaproszenie
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_bob")).andExpect(status().isConflict());
        // Cudze zaproszenie przyjac sie nie da
        wyslij("POST", "cl_cyd", "/api/clans/invitations/" + zaproszenieDla("cl_bob") + "/accept", null)
            .andExpect(status().isNotFound());

        wyslij("POST", "cl_bob", "/api/clans/invitations/" + zaproszenieDla("cl_bob") + "/accept", null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.clan.myRole").value("MEMBER"));
        em.flush();
        get_("cl_bob", "/api/notifications").andExpect(jsonPath("$.content.length()").value(0));
        get_("cl_bob", "/api/clans/" + id + "/chat").andExpect(status().isOk());

        // Ktos w klanie nie dostaje zaproszenia do drugiego ani nie przyjmie starego
        long drugi = zaloz("cl_cyd", "Rankiem", "RA");
        wyslij("POST", "cl_cyd", "/api/clans/" + drugi + "/invitations", Map.of("username", "cl_bob")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("zaproszenia: blokada, 'nikt', 'tylko znajomi' i odmowa daja ten sam komunikat; odmowa blokuje ponowne zaproszenie")
    void invitePolicies() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");

        bob.setPrivacy(ProfileVisibility.EVERYONE, InvitePolicy.EVERYONE, ClanInvitePolicy.NOBODY, true, true, false, true);
        cyd.setPrivacy(ProfileVisibility.EVERYONE, InvitePolicy.EVERYONE, ClanInvitePolicy.FRIENDS, true, true, false, true);
        em.flush();
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_bob"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Tej osoby nie można teraz zaprosić"));
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_cyd"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Tej osoby nie można teraz zaprosić"));
        ala.addFriend(cyd);
        em.flush();
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_cyd")).andExpect(status().isOk());

        // Blokada w jedna i w druga strone
        wyslij("PUT", "cl_dan", "/api/blocks/cl_ala", null).andExpect(status().isNoContent());
        em.flush();
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_dan"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Tej osoby nie można teraz zaprosić"));

        // Odmowa: przez tydzien nie da sie zaprosic ponownie - tym samym, neutralnym komunikatem
        wyslij("POST", "cl_cyd", "/api/clans/invitations/" + zaproszenieDla("cl_cyd") + "/decline", null).andExpect(status().isOk());
        em.flush();
        get_("cl_cyd", "/api/clans/mine").andExpect(jsonPath("$.invitations.length()").value(0));
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_cyd"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Tej osoby nie można teraz zaprosić"));
    }

    @Test
    @DisplayName("role: administrator klanu wyrzuca zwyklych, zalozyciel kazdego; odejscie zalozyciela wymaga przekazania")
    void rolesAndLeaving() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");
        dolacz(id, "cl_ala", "cl_cyd");
        dolacz(id, "cl_ala", "cl_dan");

        // Zwykly czlonek nikogo nie wyrzuca ani nie awansuje
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/cl_cyd", null).andExpect(status().isConflict());
        wyslij("PUT", "cl_bob", "/api/clans/" + id + "/members/cl_cyd/role", Map.of("role", "ADMIN")).andExpect(status().isConflict());
        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/members/cl_bob/role", Map.of("role", "ADMIN")).andExpect(status().isOk());
        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/members/cl_cyd/role", Map.of("role", "ADMIN")).andExpect(status().isOk());

        // Administrator: zwyklego tak, administratora i zalozyciela nie, siebie nie
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/cl_cyd", null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/cl_ala", null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/cl_bob", null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/cl_dan", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(3));
        em.flush();
        // Wyrzucony: powiadomienie bez sprawcy, klan zniknal mu z zakladki, a tresci nie widzi
        get_("cl_dan", "/api/notifications").andExpect(jsonPath("$.content[0].type").value("CLAN_KICKED"))
            .andExpect(jsonPath("$.content[0].actorUsername").doesNotExist());
        get_("cl_dan", "/api/clans/mine").andExpect(jsonPath("$.clan").doesNotExist());
        get_("cl_dan", "/api/clans/" + id + "/chat").andExpect(status().isConflict());
        // Zalozyciel wyrzuca administratora
        wyslij("DELETE", "cl_ala", "/api/clans/" + id + "/members/cl_cyd", null).andExpect(status().isOk());

        // Zalozyciel nie odchodzi bez przekazania; przekazanie robi z niego administratora
        wyslij("DELETE", "cl_ala", "/api/clans/" + id + "/members/me", null).andExpect(status().isConflict());
        wyslij("POST", "cl_bob", "/api/clans/" + id + "/transfer", Map.of("username", "cl_ala")).andExpect(status().isConflict());
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/transfer", Map.of("username", "cl_ewa")).andExpect(status().isNotFound());
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/transfer", Map.of("username", "cl_bob")).andExpect(status().isOk())
            .andExpect(jsonPath("$.myRole").value("ADMIN"));
        wyslij("DELETE", "cl_ala", "/api/clans/" + id + "/members/me", null).andExpect(status().isOk());
        em.flush();
        get_("cl_bob", "/api/clans/" + id).andExpect(jsonPath("$.myRole").value("FOUNDER")).andExpect(jsonPath("$.memberCount").value(1));

        // Sam w klanie - odejscie rozwiazuje klan
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/me", null).andExpect(status().isOk());
        // Klan kasuje sie zapytaniem, nie przez encje - test dziala w jednej sesji, wiec czyscimy jej pamiec
        em.flush();
        em.clear();
        assertThat(clanRepository.findById(id)).isEmpty();
        get_("cl_bob", "/api/clans/" + id).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("kolor: wygrywa najwiecej glosow, przy remisie wczesniejszy; odejscie glosujacego przelicza")
    void colorVoting() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");
        dolacz(id, "cl_ala", "cl_cyd");

        wyslij("PUT", "cl_dan", "/api/clans/" + id + "/color", Map.of("color", "RED")).andExpect(status().isConflict());
        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/color", Map.of("color", "ZIELONYISH")).andExpect(status().isBadRequest());

        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/color", Map.of("color", "TEAL")).andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("TEAL")).andExpect(jsonPath("$.myVote").value("TEAL"));
        // Remis 1:1 - zostaje ten, na ktory zaglosowano wczesniej
        wyslij("PUT", "cl_bob", "/api/clans/" + id + "/color", Map.of("color", "PINK")).andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("TEAL"));
        // Dwa glosy wygrywaja z jednym
        wyslij("PUT", "cl_cyd", "/api/clans/" + id + "/color", Map.of("color", "PINK")).andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("PINK")).andExpect(jsonPath("$.colorHex").value("#be185d"))
            .andExpect(jsonPath("$.palette[?(@.key == 'PINK')].votes").value(2));
        // Zmiana glosu
        wyslij("PUT", "cl_cyd", "/api/clans/" + id + "/color", Map.of("color", "BLUE")).andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("TEAL"));
        // Cofniecie glosu i odejscie glosujacego
        wyslij("DELETE", "cl_ala", "/api/clans/" + id + "/color", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.myVote").doesNotExist()).andExpect(jsonPath("$.color").value("PINK"));
        wyslij("DELETE", "cl_bob", "/api/clans/" + id + "/members/me", null).andExpect(status().isOk());
        em.flush();
        get_("cl_ala", "/api/clans/" + id).andExpect(jsonPath("$.color").value("BLUE"));
        wyslij("DELETE", "cl_cyd", "/api/clans/" + id + "/color", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("VIOLET"));
    }

    @Test
    @DisplayName("posty klanu: nie ma ich na tablicy, profilu ani pod wydarzeniem; widza je czlonkowie i administrator aplikacji")
    void clanPosts() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");

        napiszPost("cl_ala", id, "tajny plan koncertu").andExpect(status().isCreated())
            .andExpect(jsonPath("$.clan.name").value("Nocne Sowy")).andExpect(jsonPath("$.authorClan.tag").value("NS"));
        long idPosta = postRepository.findAll().stream().filter(p -> p.getClan() != null).findFirst().orElseThrow().getId();
        // Zwykly post ala na tablicy - z plakietka klanu autora
        Map<String, Object> zwykly = new HashMap<>();
        zwykly.put("content", "zwykly post");
        mvc.perform(multipart("/api/posts").file(new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE,
            json.writeValueAsBytes(zwykly))).with(user("cl_ala")).with(csrf())).andExpect(status().isCreated());
        em.flush();
        em.clear();

        assertThat(tresci("cl_bob", "/api/posts?size=50")).contains("zwykly post").doesNotContain("tajny plan koncertu");
        assertThat(tresci("cl_ewa", "/api/posts?size=50")).contains("zwykly post").doesNotContain("tajny plan koncertu");
        assertThat(tresci("cl_ewa", "/api/posts?size=50&author=cl_ala")).doesNotContain("tajny plan koncertu");
        assertThat(tresci("cl_ala", "/api/posts?size=50&author=cl_ala")).doesNotContain("tajny plan koncertu");
        assertThat(tresci("cl_ala", "/api/posts?size=50&scope=FRIENDS")).doesNotContain("tajny plan koncertu");
        get_("cl_ewa", "/api/profiles/cl_ala").andExpect(jsonPath("$.postCount").value(1))
            .andExpect(jsonPath("$.clan.name").value("Nocne Sowy"));
        get_("cl_ewa", "/api/posts?size=50").andExpect(jsonPath("$.content[?(@.content == 'zwykly post')].authorClan.tag").value("NS"));

        // Klan: czlonek i administrator aplikacji widza, obcy nie
        assertThat(tresci("cl_bob", "/api/posts?clan=" + id)).containsExactly("tajny plan koncertu");
        assertThat(tresci("cl_szef", "/api/posts?clan=" + id)).containsExactly("tajny plan koncertu");
        get_("cl_ewa", "/api/posts?clan=" + id).andExpect(status().isConflict());

        // Pojedynczy post i reakcje - dla obcego jakby go nie bylo
        get_("cl_bob", "/api/posts/" + idPosta).andExpect(status().isOk());
        get_("cl_ewa", "/api/posts/" + idPosta).andExpect(status().isNotFound());
        wyslij("PUT", "cl_ewa", "/api/posts/" + idPosta + "/reaction", Map.of("type", "FIRE")).andExpect(status().isNotFound());
        get_("cl_ewa", "/api/posts/" + idPosta + "/reactions").andExpect(status().isNotFound());
        wyslij("PUT", "cl_bob", "/api/posts/" + idPosta + "/reaction", Map.of("type", "FIRE")).andExpect(status().isOk());

        // Kasowanie: zwykly czlonek nie usunie cudzego, zalozyciel - tak
        wyslij("DELETE", "cl_ewa", "/api/posts/" + idPosta, null).andExpect(status().isNotFound());
        wyslij("DELETE", "cl_bob", "/api/posts/" + idPosta, null).andExpect(status().isConflict());
        get_("cl_bob", "/api/posts?clan=" + id).andExpect(jsonPath("$.content[0].canDelete").value(false));
        get_("cl_ala", "/api/posts?clan=" + id).andExpect(jsonPath("$.content[0].canDelete").value(true));
        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/members/cl_bob/role", Map.of("role", "ADMIN")).andExpect(status().isOk());
        wyslij("DELETE", "cl_bob", "/api/posts/" + idPosta, null).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("czat klanu: tylko dla czlonkow i administratora aplikacji; usuwa autor albo zarzad; blokada ukrywa wiadomosci")
    void chat() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");
        dolacz(id, "cl_ala", "cl_cyd");

        wyslij("POST", "cl_ala", "/api/clans/" + id + "/chat", Map.of("content", "pierwsza")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.mine").value(true)).andExpect(jsonPath("$.canDelete").value(true));
        wyslij("POST", "cl_bob", "/api/clans/" + id + "/chat", Map.of("content", "  druga  ")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("druga"));
        wyslij("POST", "cl_bob", "/api/clans/" + id + "/chat", Map.of("content", "   ")).andExpect(status().isUnprocessableEntity());
        em.flush();

        JsonNode wszystkie = json.readTree(get_("cl_cyd", "/api/clans/" + id + "/chat").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(wszystkie).hasSize(2);
        assertThat(wszystkie.get(0).get("content").asText()).isEqualTo("pierwsza");
        assertThat(wszystkie.get(1).get("mine").asBoolean()).isFalse();
        long ostatnie = wszystkie.get(1).get("id").asLong();

        wyslij("POST", "cl_ala", "/api/clans/" + id + "/chat", Map.of("content", "trzecia")).andExpect(status().isCreated());
        em.flush();
        get_("cl_cyd", "/api/clans/" + id + "/chat?after=" + ostatnie).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].content").value("trzecia"));
        get_("cl_cyd", "/api/clans/" + id + "/chat?before=" + ostatnie).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].content").value("pierwsza"));

        // Obcy nie czyta ani nie pisze; administrator aplikacji czyta
        get_("cl_ewa", "/api/clans/" + id + "/chat").andExpect(status().isConflict());
        get_("cl_szef", "/api/clans/" + id + "/chat").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        get_("cl_szef", "/api/clans/" + id).andExpect(jsonPath("$.viewingAsAdmin").value(true))
            .andExpect(jsonPath("$.canSeeContent").value(true));
        wyslij("POST", "cl_szef", "/api/clans/" + id + "/chat", Map.of("content", "wchodze")).andExpect(status().isConflict());

        // Kto usuwa: autor swoje, zarzad cudze, zwykly czlonek cudzych nie
        long wiadomoscBoba = wszystkie.get(1).get("id").asLong();
        wyslij("DELETE", "cl_cyd", "/api/clans/" + id + "/chat/" + wiadomoscBoba, null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_ewa", "/api/clans/" + id + "/chat/" + wiadomoscBoba, null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_ala", "/api/clans/" + id + "/chat/" + wiadomoscBoba, null).andExpect(status().isNoContent());

        // Blokada: wiadomosci zablokowanego znikaja u blokujacego
        wyslij("PUT", "cl_cyd", "/api/blocks/cl_ala", null).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        get_("cl_cyd", "/api/clans/" + id + "/chat").andExpect(jsonPath("$.length()").value(0));
        get_("cl_bob", "/api/clans/" + id + "/chat").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("rozwiazanie klanu: znikaja posty, czat, zaproszenia i powiadomienia; robi to zalozyciel albo administrator aplikacji")
    void disband() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_cyd")).andExpect(status().isOk());
        napiszPost("cl_bob", id, "post klanu").andExpect(status().isCreated());
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/chat", Map.of("content", "czat")).andExpect(status().isCreated());
        long idPosta = postRepository.findAll().stream().filter(p -> p.getClan() != null).findFirst().orElseThrow().getId();
        wyslij("PUT", "cl_ala", "/api/posts/" + idPosta + "/reaction", Map.of("type", "FIRE")).andExpect(status().isOk());
        em.flush();
        get_("cl_bob", "/api/notifications").andExpect(jsonPath("$.content[?(@.type == 'REACTION')]").isNotEmpty());

        wyslij("DELETE", "cl_bob", "/api/clans/" + id, null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_ewa", "/api/clans/" + id, null).andExpect(status().isConflict());
        wyslij("DELETE", "cl_szef", "/api/clans/" + id, null).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertThat(clanRepository.count()).isZero();
        assertThat(memberRepository.count()).isZero();
        assertThat(invitationRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();
        assertThat(postRepository.findAll().stream().filter(p -> p.getContent().equals("post klanu"))).isEmpty();
        get_("cl_bob", "/api/notifications").andExpect(jsonPath("$.content.length()").value(0));
        get_("cl_cyd", "/api/notifications").andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("usuniecie konta: zalozyciel przekazuje klan nastepcy, jedyny czlonek zabiera klan ze soba")
    void accountDeletion() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");
        dolacz(id, "cl_ala", "cl_cyd");
        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/members/cl_cyd/role", Map.of("role", "ADMIN")).andExpect(status().isOk());
        napiszPost("cl_ala", id, "post zalozyciela").andExpect(status().isCreated());
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/chat", Map.of("content", "wiadomosc zalozyciela")).andExpect(status().isCreated());
        wyslij("POST", "cl_ala", "/api/clans/" + id + "/invitations", Map.of("username", "cl_dan")).andExpect(status().isOk());
        em.flush();
        em.clear();

        deletion.erase(users.findByUsername("cl_ala").orElseThrow());
        em.flush();
        em.clear();

        // Administrator klanu zostal zalozycielem; posty i wiadomosci zalozyciela znikly, zaproszenie tez
        get_("cl_cyd", "/api/clans/" + id).andExpect(jsonPath("$.myRole").value("FOUNDER"))
            .andExpect(jsonPath("$.memberCount").value(2));
        assertThat(messageRepository.count()).isZero();
        assertThat(invitationRepository.count()).isZero();
        assertThat(postRepository.findAll().stream().filter(p -> p.getContent().equals("post zalozyciela"))).isEmpty();

        deletion.erase(users.findByUsername("cl_bob").orElseThrow());
        deletion.erase(users.findByUsername("cl_cyd").orElseThrow());
        em.flush();
        em.clear();
        assertThat(clanRepository.count()).isZero();
    }

    @Test
    @DisplayName("stare zaproszenie nie wpuszcza do drugiego klanu (rownolegle przyjecia) - jeden klan na osobe")
    void oneClanPerPerson() throws Exception {
        long pierwszy = zaloz("cl_ala", "Nocne Sowy", "NS");
        long drugi = zaloz("cl_cyd", "Rankiem", "RA");
        wyslij("POST", "cl_ala", "/api/clans/" + pierwszy + "/invitations", Map.of("username", "cl_bob")).andExpect(status().isOk());
        em.flush();
        long zaproszenie = zaproszenieDla("cl_bob");
        // Bob trafia do drugiego klanu "obok" - tak jak przy dwoch rownoleglych przyjeciach
        memberRepository.save(new com.musicclubapp.entity.ClanMember(clanRepository.findById(drugi).orElseThrow(),
            bob, com.musicclubapp.entity.ClanRole.MEMBER, java.time.LocalDateTime.now()));
        em.flush();

        wyslij("POST", "cl_bob", "/api/clans/invitations/" + zaproszenie + "/accept", null).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Jesteś już w klanie — najpierw z niego odejdź"));
        assertThat(memberRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("zdjecia klanu: ikona i zdjecie tylko dla zarzadu; zle pliki odrzucone")
    void images() throws Exception {
        long id = zaloz("cl_ala", "Nocne Sowy", "NS");
        dolacz(id, "cl_ala", "cl_bob");
        MockMultipartFile obraz = new MockMultipartFile("file", "ikona.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        MockMultipartFile tekst = new MockMultipartFile("file", "ikona.txt", "text/plain", "nie obraz".getBytes());

        mvc.perform(multipart("/api/clans/" + id + "/icon").file(obraz).with(user("cl_bob")).with(csrf()))
            .andExpect(status().isConflict());
        mvc.perform(multipart("/api/clans/" + id + "/icon").file(tekst).with(user("cl_ala")).with(csrf()))
            .andExpect(status().is4xxClientError());
        String icon = json.readTree(mvc.perform(multipart("/api/clans/" + id + "/icon").file(obraz)
                .with(user("cl_ala")).with(csrf())).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).get("iconUrl").asText();
        assertThat(icon).startsWith("/uploads/").endsWith(".png");
        mvc.perform(multipart("/api/clans/" + id + "/photo").file(obraz).with(user("cl_ala")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").isNotEmpty());
        wyslij("DELETE", "cl_ala", "/api/clans/" + id + "/icon", null).andExpect(jsonPath("$.iconUrl").doesNotExist());
        // Opis zmienia zarzad, nazwe tylko zalozyciel
        wyslij("PUT", "cl_ala", "/api/clans/" + id + "/members/cl_bob/role", Map.of("role", "ADMIN")).andExpect(status().isOk());
        wyslij("PUT", "cl_bob", "/api/clans/" + id, Map.of("description", "nowy opis")).andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("nowy opis"));
        wyslij("PUT", "cl_bob", "/api/clans/" + id, Map.of("name", "Inna nazwa")).andExpect(status().isConflict());
        wyslij("PUT", "cl_ala", "/api/clans/" + id, Map.of("name", "Inna nazwa", "tag", "IN")).andExpect(status().isOk())
            .andExpect(jsonPath("$.tag").value("IN"));
    }
}
