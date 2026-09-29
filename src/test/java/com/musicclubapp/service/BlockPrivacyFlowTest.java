package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.InvitePolicy;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.MusicEventRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
 * Blokady i ustawienia prywatnosci przez prawdziwe API: profil, tablica,
 * posty, reakcje, zaproszenia, propozycje, czat i lista uczestnikow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Blokady i prywatnosc - caly przebieg")
class BlockPrivacyFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PostRepository posts;
    @Autowired private MusicEventRepository events;
    @Autowired private EntityManager em;

    private User ala;
    private User ola;
    private User ewa;
    private Post postAli;
    private Post postOli;

    @BeforeEach
    void setUp() {
        ala = users.save(new User("bp_ala", "bp_ala@example.com", "x"));
        ola = users.save(new User("bp_ola", "bp_ola@example.com", "x"));
        ewa = users.save(new User("bp_ewa", "bp_ewa@example.com", "x"));
        ala.addFriend(ola);
        postAli = posts.save(publiczny(ala, "post ali"));
        postOli = posts.save(publiczny(ola, "post oli"));
        em.flush();
    }

    private static Post publiczny(User autor, String tresc) {
        Post p = new Post(autor, tresc);
        p.setVisibility(PostVisibility.PUBLIC);
        return p;
    }

    private ResultActions get_(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)));
    }

    private ResultActions zapros(String kto, String kogo) throws Exception {
        return mvc.perform(post("/api/friends/requests").with(user(kto)).with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + kogo + "\"}"));
    }

    private List<String> tablica(String kto) throws Exception {
        JsonNode strona = json.readTree(get_(kto, "/api/posts?size=50").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        List<String> tresci = new ArrayList<>();
        strona.get("content").forEach(p -> tresci.add(p.get("content").asText()));
        return tresci;
    }

    private List<String> propozycje(String kto) throws Exception {
        JsonNode lista = json.readTree(get_(kto, "/api/friends/suggestions?limit=60").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        List<String> loginy = new ArrayList<>();
        lista.forEach(s -> loginy.add(s.get("username").asText()));
        return loginy;
    }

    private void blokuj(String kto, String kogo) throws Exception {
        mvc.perform(put("/api/blocks/" + kogo).with(user(kto)).with(csrf())).andExpect(status().isNoContent());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("blokada: znajomosc znika, profil blokujacego nie istnieje, posty znikaja w obie strony")
    void blockHidesEverythingBothWays() throws Exception {
        assertThat(tablica("bp_ola")).contains("post ali");

        blokuj("bp_ala", "bp_ola");

        assertThat(users.areFriends("bp_ala", "bp_ola")).isFalse();

        // Zablokowana nie wie o blokadzie: profil wyglada jak nieistniejacy
        get_("bp_ola", "/api/profiles/bp_ala").andExpect(status().isNotFound());
        get_("bp_ola", "/api/profiles/bp_ala/favorites").andExpect(status().isNotFound());
        // Blokujaca widzi login i "Odblokuj", ale bez szczegolow
        get_("bp_ala", "/api/profiles/bp_ola").andExpect(status().isOk())
            .andExpect(jsonPath("$.blockedByMe").value(true))
            .andExpect(jsonPath("$.postCount").value(0))
            .andExpect(jsonPath("$.canInvite").value(false));
        get_("bp_ala", "/api/profiles/bp_ola/favorites").andExpect(status().isConflict());

        assertThat(tablica("bp_ola")).doesNotContain("post ali");
        assertThat(tablica("bp_ala")).doesNotContain("post oli");
        get_("bp_ola", "/api/posts/" + postAli.getId()).andExpect(status().isNotFound());
        mvc.perform(put("/api/posts/" + postAli.getId() + "/reaction").with(user("bp_ola")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"FIRE\"}"))
            .andExpect(status().isNotFound());

        // Zaproszenia - w obie strony ten sam komunikat, bez slowa o blokadzie
        zapros("bp_ola", "bp_ala").andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Tej osoby nie można teraz zaprosić"));
        zapros("bp_ala", "bp_ola").andExpect(status().isConflict());

        // Czat tylko dla znajomych - pisac sie juz nie da
        mvc.perform(post("/api/messages/with/bp_ala").with(user("bp_ola")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hej\"}"))
            .andExpect(status().isConflict());

        assertThat(propozycje("bp_ola")).doesNotContain("bp_ala").contains("bp_ewa");
        assertThat(propozycje("bp_ala")).doesNotContain("bp_ola");

        // Odblokowanie - wszystko wraca, poza znajomoscia
        mvc.perform(delete("/api/blocks/bp_ola").with(user("bp_ala")).with(csrf())).andExpect(status().isNoContent());
        get_("bp_ola", "/api/profiles/bp_ala").andExpect(status().isOk())
            .andExpect(jsonPath("$.friendshipStatus").value("NONE"));
        assertThat(tablica("bp_ola")).contains("post ali");
    }

    @Test
    @DisplayName("lista zablokowanych i blokada samego siebie")
    void blockList() throws Exception {
        blokuj("bp_ala", "bp_ewa");
        get_("bp_ala", "/api/blocks").andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("bp_ewa"));
        mvc.perform(put("/api/blocks/bp_ala").with(user("bp_ala")).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("zablokowany uczestnik znika z listy idacych - licznik go liczy")
    void blockedAttendeeIsHidden() throws Exception {
        MusicEvent koncert = WydarzeniaTestowe.wydarzenie("bp-1", "Koncert", LocalDate.now().plusDays(30),
            "Warsaw", "Rock", "Rock", "Ktos");
        events.save(koncert);
        em.flush();
        for (String kto : List.of("bp_ola", "bp_ewa")) {
            mvc.perform(put("/api/events/" + koncert.getId() + "/participation").with(user(kto)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOING\"}"))
                .andExpect(status().isOk());
        }
        blokuj("bp_ala", "bp_ola");

        get_("bp_ala", "/api/events/" + koncert.getId() + "/attendees").andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].username").value("bp_ewa"));
    }

    @Test
    @DisplayName("profil tylko dla znajomych: obcy widzi sam login, znajomy i administrator - wszystko")
    void friendsOnlyProfile() throws Exception {
        mvc.perform(put("/api/profile/privacy").with(user("bp_ala")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"profileVisibility":"FRIENDS","friendRequestsFrom":"EVERYONE",
                     "showOnline":true,"showInSuggestions":true,"hideOnAttendeeLists":false}
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profileVisibility").value("FRIENDS"));

        get_("bp_ewa", "/api/profiles/bp_ala").andExpect(status().isOk())
            .andExpect(jsonPath("$.restricted").value(true))
            .andExpect(jsonPath("$.postCount").value(0))
            .andExpect(jsonPath("$.canInvite").value(true));
        get_("bp_ewa", "/api/profiles/bp_ala/favorites").andExpect(status().isConflict());
        get_("bp_ewa", "/api/posts?author=bp_ala").andExpect(status().isConflict());

        get_("bp_ola", "/api/profiles/bp_ala").andExpect(jsonPath("$.restricted").value(false));
        get_("bp_ola", "/api/profiles/bp_ala/favorites").andExpect(status().isOk());

        User admin = users.save(new User("bp_admin", "bp_admin@example.com", "x"));
        admin.setRole(Role.ADMIN);
        em.flush();
        mvc.perform(get("/api/profiles/bp_ala/favorites").with(user("bp_admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("kto moze zaprosic: nikt / tylko znajomi znajomych")
    void invitePolicies() throws Exception {
        User nowy = users.save(new User("bp_nowy", "bp_nowy@example.com", "x"));
        nowy.setPrivacy(ProfileVisibility.EVERYONE, InvitePolicy.NOBODY, true, true, false);
        em.flush();
        get_("bp_ewa", "/api/profiles/bp_nowy").andExpect(jsonPath("$.canInvite").value(false));
        zapros("bp_ewa", "bp_nowy").andExpect(status().isConflict());

        // Znajomi znajomych: ewa i ola nie maja wspolnych znajomych - zaproszenie odpada
        ola.setPrivacy(ProfileVisibility.EVERYONE, InvitePolicy.FRIENDS_OF_FRIENDS, true, true, false);
        em.flush();
        zapros("bp_ewa", "bp_ola").andExpect(status().isConflict());
        ala = users.findByUsername("bp_ala").orElseThrow();
        ewa = users.findByUsername("bp_ewa").orElseThrow();
        ala.addFriend(ewa);
        em.flush();
        // Teraz ala jest wspolna znajoma ewy i oli - zaproszenie przechodzi
        zapros("bp_ewa", "bp_ola").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ukryta aktywnosc, brak w propozycjach, domyslnie ukryty na liscie uczestnikow")
    void otherSettings() throws Exception {
        User ukryta = users.save(new User("bp_cicha", "bp_cicha@example.com", "x"));
        ukryta.setLastSeenAt(LocalDateTime.now());
        ukryta.setPrivacy(ProfileVisibility.EVERYONE, InvitePolicy.EVERYONE, false, false, true);
        em.flush();

        get_("bp_ewa", "/api/profiles/bp_cicha").andExpect(jsonPath("$.presence.online").value(false))
            .andExpect(jsonPath("$.presence.lastSeenAt").doesNotExist())
            .andExpect(jsonPath("$.presence.hidden").value(true));
        assertThat(propozycje("bp_ewa")).doesNotContain("bp_cicha");

        MusicEvent koncert = WydarzeniaTestowe.wydarzenie("bp-2", "Koncert 2", LocalDate.now().plusDays(30),
            "Warsaw", "Rock", "Rock", "Ktos");
        events.save(koncert);
        em.flush();
        mvc.perform(put("/api/events/" + koncert.getId() + "/participation").with(user("bp_cicha")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOING\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true));
        // Jawne "pokaz mnie" przy zmianie wygrywa z domyslnym
        mvc.perform(put("/api/events/" + koncert.getId() + "/participation").with(user("bp_cicha")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOING\",\"hidden\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(false));
    }
}
