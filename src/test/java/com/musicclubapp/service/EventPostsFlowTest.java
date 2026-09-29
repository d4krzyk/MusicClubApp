package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.MusicEventRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Posty pod wydarzeniem ("szukam ekipy"): dodawanie, lista pod wydarzeniem,
 * plakietka na tablicy i te same zasady widocznosci co przy zwyklych postach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Posty pod wydarzeniem - caly przebieg")
class EventPostsFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private MusicEventRepository events;
    @Autowired private EntityManager em;

    private MusicEvent koncert;
    private MusicEvent inny;

    @BeforeEach
    void setUp() {
        User ala = users.save(new User("ep_ala", "ep_ala@example.com", "x"));
        User ola = users.save(new User("ep_ola", "ep_ola@example.com", "x"));
        users.save(new User("ep_ewa", "ep_ewa@example.com", "x"));
        ala.addFriend(ola);
        koncert = events.save(WydarzeniaTestowe.wydarzenie("EP1", "Nocny koncert",
            LocalDate.of(2026, 10, 10), "Kraków", "Rock", null, "Zespol"));
        inny = events.save(WydarzeniaTestowe.wydarzenie("EP2", "Inny koncert",
            LocalDate.of(2026, 10, 11), "Kraków", "Rock", null, "Inni"));
        em.flush();
    }

    private ResultActions dodaj(String kto, String tresc, String widocznosc, Long wydarzenie) throws Exception {
        java.util.Map<String, Object> pola = new java.util.HashMap<>();
        pola.put("content", tresc);
        pola.put("visibility", widocznosc);
        pola.put("eventId", wydarzenie);
        String dane = json.writeValueAsString(pola);
        MockMultipartFile post = new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE, dane.getBytes());
        return mvc.perform(multipart("/api/posts").file(post).with(user(kto)).with(csrf()));
    }

    private List<String> tresci(String kto, String adres) throws Exception {
        JsonNode strona = json.readTree(mvc.perform(get(adres).with(user(kto)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> wynik = new ArrayList<>();
        strona.get("content").forEach(p -> wynik.add(p.get("content").asText()));
        return wynik;
    }

    private String podWydarzeniem(MusicEvent e) {
        return "/api/posts?size=50&event=" + e.getId();
    }

    @Test
    @DisplayName("post pod wydarzeniem: odpowiedz z wydarzeniem, na liscie pod nim i na tablicy z plakietka")
    void postUnderEvent() throws Exception {
        dodaj("ep_ala", "kto idzie na Nocny?", "PUBLIC", koncert.getId())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.event.id").value(koncert.getId()))
            .andExpect(jsonPath("$.event.name").value("Nocny koncert"))
            .andExpect(jsonPath("$.event.date").value("2026-10-10"))
            .andExpect(jsonPath("$.event.cityKey").value("krakow"));
        dodaj("ep_ala", "zwykly post", "PUBLIC", null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.event").doesNotExist());
        em.flush();
        em.clear();

        assertThat(tresci("ep_ewa", podWydarzeniem(koncert))).containsExactly("kto idzie na Nocny?");
        assertThat(tresci("ep_ewa", podWydarzeniem(inny))).isEmpty();
        // Dziala tak samo jak zwykly post: jest na tablicy, z odnosnikiem do wydarzenia
        mvc.perform(get("/api/posts?size=50").with(user("ep_ewa")))
            .andExpect(jsonPath("$.content[?(@.content == 'kto idzie na Nocny?')].event.name").value("Nocny koncert"));
    }

    @Test
    @DisplayName("nieistniejace wydarzenie: 404 przy dodawaniu i przy liscie")
    void missingEvent() throws Exception {
        dodaj("ep_ala", "gdzie to?", "PUBLIC", 999_999L).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts?event=999999").with(user("ep_ala"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("'tylko dla znajomych' pod wydarzeniem widzi krag autora; zablokowany nie widzi nic")
    void visibilityAndBlocks() throws Exception {
        dodaj("ep_ala", "dla ekipy", "FRIENDS", koncert.getId()).andExpect(status().isCreated());
        dodaj("ep_ala", "dla wszystkich", "PUBLIC", koncert.getId()).andExpect(status().isCreated());
        em.flush();

        assertThat(tresci("ep_ola", podWydarzeniem(koncert))).containsExactlyInAnyOrder("dla ekipy", "dla wszystkich");
        assertThat(tresci("ep_ewa", podWydarzeniem(koncert))).containsExactly("dla wszystkich");

        mvc.perform(put("/api/blocks/ep_ala").with(user("ep_ewa")).with(csrf())).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(tresci("ep_ewa", podWydarzeniem(koncert))).isEmpty();
        assertThat(tresci("ep_ala", podWydarzeniem(koncert))).hasSize(2);
    }
}
