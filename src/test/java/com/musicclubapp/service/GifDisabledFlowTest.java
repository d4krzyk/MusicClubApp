package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.PostRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bez klucza GIF-y sa wylaczone, a reszta komentarzy i czatu dziala jak dotad. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GIF-y wylaczone (brak GIF_API_KEY)")
class GifDisabledFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PostRepository posts;
    @Autowired private EntityManager em;

    @MockBean private PushService push;

    private Post post;

    @BeforeEach
    void setUp() {
        User ala = users.save(new User("gw_ala", "gw_ala@example.com", "x"));
        User bob = users.save(new User("gw_bob", "gw_bob@example.com", "x"));
        ala.addFriend(bob);
        users.save(ala);
        users.save(bob);
        post = posts.save(new Post(ala, "post"));
        em.flush();
    }

    private ResultActions wyslij(String kto, String adres, Object tresc) throws Exception {
        return mvc.perform(post(adres).with(user(kto)).with(csrf()).header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc)));
    }

    @Test
    @DisplayName("status mowi, ze GIF-y sa wylaczone; wyszukiwanie to 503")
    void searchIsOff() throws Exception {
        JsonNode s = json.readTree(mvc.perform(get("/api/gifs/status").with(user("gw_ala"))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(s.get("enabled").asBoolean()).isFalse();
        assertThat(s.get("attribution").isNull()).isTrue();

        String komunikat = mvc.perform(get("/api/gifs/search?q=kot").with(user("gw_ala")).header("Accept-Language", "pl"))
            .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(komunikat).contains("GIF-y nie są włączone");
    }

    @Test
    @DisplayName("token GIF-a w komentarzu i w wiadomosci jest odrzucany, a zwykly komentarz i wiadomosc dzialaja")
    void attachmentsAreRefused() throws Exception {
        wyslij("gw_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "x", "gif", "jakis.token"))
            .andExpect(status().isServiceUnavailable());
        wyslij("gw_bob", "/api/messages/with/gw_ala", Map.of("gif", "jakis.token")).andExpect(status().isServiceUnavailable());

        wyslij("gw_bob", "/api/posts/" + post.getId() + "/comments", Map.of("content", "zwykly")).andExpect(status().isCreated());
        wyslij("gw_bob", "/api/messages/with/gw_ala", Map.of("content", "zwykla")).andExpect(status().isCreated());

        // czat klanu tak samo: GIF - 503 i nic nie zapisane, sam tekst - dziala
        long klan = json.readTree(wyslij("gw_ala", "/api/clans", Map.of("name", "Bez Gifow", "tag", "BG"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("id").asLong();
        wyslij("gw_ala", "/api/clans/" + klan + "/chat", Map.of("content", "x", "gif", "jakis.token"))
            .andExpect(status().isServiceUnavailable());
        wyslij("gw_ala", "/api/clans/" + klan + "/chat", Map.of("content", "zwykla")).andExpect(status().isCreated());
    }
}
