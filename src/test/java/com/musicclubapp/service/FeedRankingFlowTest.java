package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.FeedSort;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tablica "Dla ciebie": znajomi na gorze, pod nimi obcy wedlug okolicy, gustu i reakcji - przez serwis
 * i przez prawdziwe API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Tablica Dla ciebie - ranking postow obcych")
class FeedRankingFlowTest {

    @Autowired private PostService postService;
    @Autowired private ReactionService reactionService;
    @Autowired private BlockService blocks;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ArtistRepository artists;
    @Autowired private EntityManager em;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private User ja;

    @BeforeEach
    void setUp() {
        ja = osoba("fe_ja", "Poznań", 52.4064, 16.9252);
    }

    private User osoba(String login, String miasto, Double lat, Double lon) {
        User u = new User(login, login + "@example.com", "x");
        if (miasto != null) {
            u.setCity(miasto, EventImportService.cityKey(miasto), lat, lon);
        }
        return userRepository.save(u);
    }

    private User krakow(String login) {
        return osoba(login, "Kraków", 50.0647, 19.9450);
    }

    private Post post(User autor, String tresc, PostVisibility widocznosc, int minutTemu) {
        Post post = new Post(autor, tresc);
        post.setVisibility(widocznosc);
        postRepository.save(post);
        em.createQuery("UPDATE Post p SET p.createdAt = :moment WHERE p.id = :id")
            .setParameter("moment", LocalDateTime.now().minusMinutes(minutTemu))
            .setParameter("id", post.getId()).executeUpdate();
        return post;
    }

    private Post post(User autor, String tresc, int minutTemu) {
        return post(autor, tresc, PostVisibility.PUBLIC, minutTemu);
    }

    private static final int GODZ = 60;

    private List<String> tablica(String kto, FeedSort sort) {
        em.flush();
        em.clear();
        return postService.feed(kto, FeedScope.ALL, sort, PageRequest.of(0, 50)).getContent().stream()
            .map(PostResponse::content).toList();
    }

    /** Wspolny scenariusz: znajomy, a pod nim obcy o roznych cechach. */
    private void scenariusz() {
        Artist wspolny = artists.save(new Artist("fe-a", "Wspolny Artysta", null));
        ja.getFavoriteArtists().add(wspolny);

        User znajomy = osoba("fe_znajomy", null, null, null);
        ja.addFriend(znajomy);
        userRepository.save(ja);
        userRepository.save(znajomy);

        post(znajomy, "znajomy starszy", 3 * 24 * GODZ);

        User bliski = osoba("fe_bliski", "Poznań", 52.4064, 16.9252);
        User ukryteMiasto = osoba("fe_ukryte", "Poznań", 52.4064, 16.9252);
        ukryteMiasto.setPrivacy(ProfileVisibility.EVERYONE, ukryteMiasto.getFriendRequestsFrom(),
            ukryteMiasto.getClanInvitesFrom(), true, true, false, false);
        User gust = krakow("fe_gust");
        gust.getFavoriteArtists().add(wspolny);
        User popularny = krakow("fe_popularny");
        User zwykly = krakow("fe_zwykly");
        User stary = krakow("fe_stary");
        User ograniczony = krakow("fe_ograniczony");
        ograniczony.getFavoriteArtists().add(wspolny);
        ograniczony.setPrivacy(ProfileVisibility.FRIENDS, ograniczony.getFriendRequestsFrom(),
            ograniczony.getClanInvitesFrom(), true, true, false, true);
        User zablokowany = osoba("fe_zablokowany", "Poznań", 52.4064, 16.9252);
        em.flush();

        post(bliski, "bliski 5h", 5 * GODZ);
        post(ukryteMiasto, "ukryte miasto 6h", 6 * GODZ);
        post(gust, "gust 5h", 5 * GODZ + 1);
        Post pop = post(popularny, "popularny 5h", 5 * GODZ + 2);
        post(zwykly, "zwykly 1h", 1 * GODZ);
        post(ograniczony, "ograniczony 5h", 5 * GODZ + 3);
        post(stary, "stary 9 dni", 9 * 24 * GODZ);
        post(bliski, "bliski tylko dla znajomych", PostVisibility.FRIENDS, 1);
        post(zablokowany, "zablokowany", 1);
        em.flush();

        for (int i = 1; i <= 6; i++) {
            osoba("fe_r" + i, null, null, null);
        }
        em.flush();
        for (int i = 1; i <= 6; i++) {
            reactionService.set(pop.getId(), "fe_r" + i, ReactionType.FIRE);
        }
        blocks.block("fe_ja", "fe_zablokowany");
    }

    @Test
    @DisplayName("znajomi na gorze, pod nimi obcy od najlepiej ocenionych; bez blokad i postow tylko dla znajomych")
    void relevantOrder() {
        scenariusz();
        assertThat(tablica("fe_ja", FeedSort.RELEVANT)).containsExactly(
            "znajomy starszy",
            "bliski 5h",          // to samo miasto, swiezy
            "ukryte miasto 6h",   // okolica liczy sie do kolejnosci, choc autor jej nie pokazuje
            "popularny 5h",       // 6 reakcji
            "gust 5h",            // wspolny wykonawca
            "zwykly 1h",          // sam swiezy
            "ograniczony 5h",     // wspolny wykonawca, ale profil tylko dla znajomych - gust sie nie liczy
            "stary 9 dni");
    }

    @Test
    @DisplayName("od najnowszych: znajomi, potem obcy po dacie - tak jak przed rankingiem")
    void newestOrder() {
        scenariusz();
        assertThat(tablica("fe_ja", FeedSort.NEWEST)).containsExactly(
            "znajomy starszy",
            "zwykly 1h", "bliski 5h", "gust 5h", "popularny 5h", "ograniczony 5h", "ukryte miasto 6h", "stary 9 dni");
    }

    @Test
    @DisplayName("kto nie ma miasta ani gustu, dostaje swiezosc i popularnosc: popularny post wyprzedza swiezy zwykly")
    void withoutLocationAndTaste() {
        scenariusz();
        osoba("fe_nowy", null, null, null);
        List<String> t = tablica("fe_nowy", FeedSort.RELEVANT);
        // zwykly 1h: 0,99 x 0,4 = 0,40; popularny 5h: 0,95 x (0,4 + 0,38) = 0,74
        assertThat(t.indexOf("popularny 5h")).isLessThan(t.indexOf("zwykly 1h"));
        // bez miasta "bliski" nie ma przewagi nad "zwykly 1h" - starszy i pozbawiony czegokolwiek
        assertThat(t.indexOf("zwykly 1h")).isLessThan(t.indexOf("bliski 5h"));
    }

    @Test
    @DisplayName("scope=FRIENDS nie zna rankingu - sam krag, od najnowszych")
    void friendsScopeIgnoresRanking() {
        scenariusz();
        em.flush();
        em.clear();
        List<String> t = postService.feed("fe_ja", FeedScope.FRIENDS, FeedSort.RELEVANT, PageRequest.of(0, 50))
            .getContent().stream().map(PostResponse::content).toList();
        assertThat(t).containsExactly("znajomy starszy");
    }

    @Test
    @DisplayName("API: domyslnie ranking, powody przy postach obcych; sort=NEWEST wylacza ranking i powody")
    void apiDefaultsToRelevant() throws Exception {
        scenariusz();
        em.flush();
        em.clear();
        JsonNode domyslnie = json.readTree(mvc.perform(get("/api/posts?size=50").with(user("fe_ja")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> tresci = new ArrayList<>();
        domyslnie.get("content").forEach(p -> tresci.add(p.get("content").asText()));
        assertThat(tresci.get(1)).isEqualTo("bliski 5h");

        JsonNode bliski = domyslnie.get("content").get(1);
        assertThat(bliski.get("feedReasons").toString()).isEqualTo("[\"NEAR\"]");
        JsonNode ukryte = domyslnie.get("content").get(2);
        assertThat(ukryte.get("feedReasons").size()).isZero();
        assertThat(domyslnie.get("content").get(3).get("feedReasons").toString()).isEqualTo("[\"POPULAR\"]");
        assertThat(domyslnie.get("content").get(4).get("feedReasons").toString()).isEqualTo("[\"TASTE\"]");
        assertThat(domyslnie.get("content").get(0).get("feedReasons").size()).isZero();
        assertThat(domyslnie.get("totalElements").asInt()).isEqualTo(8);

        JsonNode najnowsze = json.readTree(mvc.perform(get("/api/posts?size=50&sort=NEWEST").with(user("fe_ja")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(najnowsze.get("content").get(1).get("content").asText()).isEqualTo("zwykly 1h");
        najnowsze.get("content").forEach(p -> assertThat(p.get("feedReasons").size()).isZero());
    }

    @Test
    @DisplayName("suma na niepelnej stronie nie wlicza osob z blokad (ostatnia strona i tak sama sie poprawia)")
    void totalIgnoresBlockedAuthors() {
        scenariusz();
        em.flush();
        em.clear();
        Page<PostResponse> pierwsza = postService.feed("fe_ja", FeedScope.ALL, FeedSort.RELEVANT, PageRequest.of(0, 3));
        // 1 post znajomego + 7 postow obcych; zablokowany i post "tylko dla znajomych" obcego nie licza sie
        assertThat(pierwsza.getTotalElements()).isEqualTo(8);
        assertThat(pierwsza.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("bez zadnych postow i ze samymi postami kregu tablica nie wybucha")
    void emptyAndFriendsOnly() {
        assertThat(tablica("fe_ja", FeedSort.RELEVANT)).isEmpty();
        User znajomy = osoba("fe_z", null, null, null);
        ja.addFriend(znajomy);
        userRepository.save(ja);
        userRepository.save(znajomy);
        post(znajomy, "tylko znajomy", 10);
        post(ja, "moj", 20);
        assertThat(tablica("fe_ja", FeedSort.RELEVANT)).containsExactly("tylko znajomy", "moj");
        Page<PostResponse> p = postService.feed("fe_ja", FeedScope.ALL, FeedSort.RELEVANT, PageRequest.of(0, 1));
        assertThat(p.getTotalElements()).isEqualTo(2);
        assertThat(p.isLast()).isFalse();
    }
}
