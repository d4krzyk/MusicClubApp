package com.musicclubapp.service;

import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.FeedSort;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Przewijanie tablicy "Dla ciebie": znajomi, potem ranking najnowszych obcych, potem reszta od najnowszych.
 * Pula rankingu to tu 3 posty (zamiast 300), zeby granice dalo sie przejsc kilkoma postami.
 */
@SpringBootTest(properties = "app.feed.pool-size=3")
@ActiveProfiles("test")
@Transactional
@DisplayName("Tablica Dla ciebie - przewijanie przez granice puli rankingu")
class FeedPaginationTest {

    @Autowired private PostService postService;
    @Autowired private ReactionService reactionService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        User ja = userRepository.save(new User("pg_ja", "pg_ja@example.com", "x"));
        User znajomy = userRepository.save(new User("pg_z", "pg_z@example.com", "x"));
        ja.addFriend(znajomy);
        userRepository.save(ja);
        userRepository.save(znajomy);
        User obcy = userRepository.save(new User("pg_o", "pg_o@example.com", "x"));
        for (int i = 1; i <= 6; i++) {
            userRepository.save(new User("pg_r" + i, "pg_r" + i + "@example.com", "x"));
        }
        em.flush();

        post(znajomy, "z1", 10);
        post(znajomy, "z2", 20);
        // obcy: s1 (1 h) ... s8 (8 h); s3 jest popularny (w puli - wyskoczy na pierwsze miejsce),
        // a s6 tez popularny, ale poza pula - zostaje na swoim miejscu wsrod "od najnowszych"
        Post s3 = null;
        Post s6 = null;
        for (int i = 1; i <= 8; i++) {
            Post p = post(obcy, "s" + i, i * 60);
            if (i == 3) {
                s3 = p;
            }
            if (i == 6) {
                s6 = p;
            }
        }
        em.flush();
        for (int i = 1; i <= 6; i++) {
            reactionService.set(s3.getId(), "pg_r" + i, ReactionType.FIRE);
            reactionService.set(s6.getId(), "pg_r" + i, ReactionType.FIRE);
        }
        em.flush();
        em.clear();
    }

    private Post post(User autor, String tresc, int minutTemu) {
        Post post = new Post(autor, tresc);
        post.setVisibility(PostVisibility.PUBLIC);
        postRepository.save(post);
        em.createQuery("UPDATE Post p SET p.createdAt = :moment WHERE p.id = :id")
            .setParameter("moment", LocalDateTime.now().minusMinutes(minutTemu))
            .setParameter("id", post.getId()).executeUpdate();
        return post;
    }

    private List<String> strona(int numer, int rozmiar) {
        em.clear();
        return postService.feed("pg_ja", FeedScope.ALL, FeedSort.RELEVANT, PageRequest.of(numer, rozmiar))
            .getContent().stream().map(PostResponse::content).toList();
    }

    @Test
    @DisplayName("ranking tylko w puli 3 najnowszych obcych: s3 wyskakuje na przod, s6 (popularny, ale poza pula) zostaje")
    void rankingOnlyInsidePool() {
        assertThat(strona(0, 50)).containsExactly("z1", "z2", "s3", "s1", "s2", "s4", "s5", "s6", "s7", "s8");
    }

    @Test
    @DisplayName("strony po dwa posty skladaja sie w te sama liste, bez powtorzen i dziur - takze na granicy puli")
    void pagesAddUpToTheWholeList() {
        List<String> razem = new ArrayList<>();
        for (int numer = 0; numer < 5; numer++) {
            List<String> s = strona(numer, 2);
            assertThat(s).hasSize(2);
            razem.addAll(s);
        }
        assertThat(razem).containsExactly("z1", "z2", "s3", "s1", "s2", "s4", "s5", "s6", "s7", "s8");
        assertThat(strona(5, 2)).isEmpty();
        // inne rozmiary stron tez daja ta sama liste
        for (int rozmiar : new int[] {1, 3, 4, 7}) {
            List<String> inne = new ArrayList<>();
            for (int numer = 0; inne.size() < 10; numer++) {
                inne.addAll(strona(numer, rozmiar));
            }
            assertThat(inne).as("rozmiar strony " + rozmiar).containsExactly(
                "z1", "z2", "s3", "s1", "s2", "s4", "s5", "s6", "s7", "s8");
        }
    }

    @Test
    @DisplayName("suma i ostatnia strona zgadzaja sie z lista skladana z trzech kawalkow")
    void totalsAndLastPage() {
        em.clear();
        Page<PostResponse> pierwsza = postService.feed("pg_ja", FeedScope.ALL, FeedSort.RELEVANT, PageRequest.of(0, 4));
        assertThat(pierwsza.getTotalElements()).isEqualTo(10);
        assertThat(pierwsza.getTotalPages()).isEqualTo(3);
        assertThat(pierwsza.isLast()).isFalse();
        em.clear();
        Page<PostResponse> ostatnia = postService.feed("pg_ja", FeedScope.ALL, FeedSort.RELEVANT, PageRequest.of(2, 4));
        assertThat(ostatnia.getContent()).hasSize(2);
        assertThat(ostatnia.isLast()).isTrue();
    }

    @Test
    @DisplayName("powody tylko przy postach z rankingu; reszta od najnowszych ich nie ma")
    void reasonsOnlyForRankedPosts() {
        em.clear();
        List<PostResponse> wszystkie = postService.feed("pg_ja", FeedScope.ALL, FeedSort.RELEVANT, PageRequest.of(0, 50)).getContent();
        assertThat(wszystkie.get(2).content()).isEqualTo("s3");
        assertThat(wszystkie.get(2).feedReasons().toString()).isEqualTo("[POPULAR]");
        // s6 jest popularny, ale poza pula - tak jak jego kolejnosc, nie dostaje tez powodu
        PostResponse s6 = wszystkie.stream().filter(p -> p.content().equals("s6")).findFirst().orElseThrow();
        assertThat(s6.feedReasons()).isEmpty();
    }
}
