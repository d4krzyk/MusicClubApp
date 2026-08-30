package com.musicclubapp.service;

import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Widocznosc postow i kolejnosc tablicy - na prawdziwej bazie. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Widocznosc postow i kolejnosc tablicy")
class PostVisibilityTest {

    @Autowired private PostService postService;
    @Autowired private ReactionService reactionService;
    @Autowired private PublicProfileService publicProfileService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User ja;
    private User kumpel;
    private User obcy;
    private User admin;

    @BeforeEach
    void setUp() {
        ja = userRepository.save(new User("ja", "ja@example.com", "hash"));
        kumpel = userRepository.save(new User("kumpel", "kumpel@example.com", "hash"));
        obcy = userRepository.save(new User("obcy", "obcy@example.com", "hash"));

        /*
         * NIE "admin": konto o tej nazwie zaklada przy starcie AdminInitializer, a login jest
         * unikalny w bazie.
         */
        admin = new User("szef", "szef@example.com", "hash");
        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        ja.addFriend(kumpel);
        userRepository.save(ja);
        userRepository.save(kumpel);

        /*
         * Zapytania natywne (circleIds) czytaja WPROST z bazy, wiec zmiany musza tam byc, zanim je
         * zawolamy - stad jawne flush().
         */
        entityManager.flush();
    }

    /** Zapisuje post z narzucona data. */
    private Post post(User author, String content, PostVisibility visibility, int minutesAgo) {
        Post post = new Post(author, content);
        post.setVisibility(visibility);
        postRepository.save(post);

        entityManager.createQuery("UPDATE Post p SET p.createdAt = :moment WHERE p.id = :id")
            .setParameter("moment", LocalDateTime.now().minusMinutes(minutesAgo))
            .setParameter("id", post.getId())
            .executeUpdate();

        return post;
    }

    /** Piec postow: dwa moje kregu, trzy obcego - w tym jeden ukryty. */
    private void fiveMixedPosts() {
        post(obcy, "obcy publicznie NAJNOWSZY", PostVisibility.PUBLIC, 1);
        post(obcy, "obcy tylko dla swoich", PostVisibility.FRIENDS, 2);
        post(kumpel, "kumpel tylko dla swoich", PostVisibility.FRIENDS, 3);
        post(kumpel, "kumpel publicznie", PostVisibility.PUBLIC, 4);
        post(ja, "moj wlasny NAJSTARSZY", PostVisibility.PUBLIC, 5);
        entityManager.flush();
    }

    private List<String> feed(String viewer, FeedScope scope) {
        return postService.feed(viewer, scope, PageRequest.of(0, 20))
            .getContent().stream()
            .map(PostResponse::content)
            .toList();
    }

    @Test
    @DisplayName("posty znajomych sa NAD postami obcych, mimo ze sa starsze")
    void friendsComeFirst() {
        fiveMixedPosts();

        /*
         * Post obcego jest najnowszy ze wszystkich, wiec przy zwyklym sortowaniu po dacie bylby na
         * samej gorze.
         */
        assertThat(feed("ja", FeedScope.ALL)).containsExactly(
            "kumpel tylko dla swoich",
            "kumpel publicznie",
            "moj wlasny NAJSTARSZY",
            "obcy publicznie NAJNOWSZY");
    }

    @Test
    @DisplayName("post 'tylko dla znajomych' nie trafia na tablice obcej osoby")
    void friendsOnlyPostIsHiddenFromStrangers() {
        fiveMixedPosts();

        assertThat(feed("obcy", FeedScope.ALL))
            .doesNotContain("kumpel tylko dla swoich")
            .contains("kumpel publicznie", "obcy tylko dla swoich");
    }

    @Test
    @DisplayName("administrator TEZ nie widzi cudzego posta dla znajomych")
    void adminIsNotAnException() {
        fiveMixedPosts();

        /* To nie jest przeoczenie, tylko decyzja - patrz PostVisibility.FRIENDS. */
        assertThat(feed("szef", FeedScope.ALL))
            .doesNotContain("kumpel tylko dla swoich", "obcy tylko dla swoich");
    }

    @Test
    @DisplayName("zawezenie do znajomych zostawia tylko moj krag")
    void friendsScopeDropsStrangers() {
        fiveMixedPosts();

        assertThat(feed("ja", FeedScope.FRIENDS)).containsExactly(
            "kumpel tylko dla swoich",
            "kumpel publicznie",
            "moj wlasny NAJSTARSZY");
    }

    @Test
    @DisplayName("nowy uzytkownik bez znajomych widzi publiczne posty innych")
    void emptyCircleStillSeesTheWorld() {
        fiveMixedPosts();

        User nowy = userRepository.save(new User("nowy", "nowy@example.com", "hash"));
        entityManager.flush();

        /*
         * Konto bez ani jednego znajomego to zbior pusty, a "IN ()" jest w SQL-u bledem skladni.
         */
        assertThat(feed(nowy.getUsername(), FeedScope.ALL))
            .containsExactly("obcy publicznie NAJNOWSZY", "kumpel publicznie",
                "moj wlasny NAJSTARSZY");
    }

    @Test
    @DisplayName("stronicowanie nie gubi ani nie powtarza postow")
    void pagingIsConsistent() {
        fiveMixedPosts();

        List<String> first = postService.feed("ja", FeedScope.ALL, PageRequest.of(0, 2))
            .getContent().stream().map(PostResponse::content).toList();
        var second = postService.feed("ja", FeedScope.ALL, PageRequest.of(1, 2));

        /* To jest powod, dla ktorego kolejnosc liczy BAZA, a nie Java. */
        assertThat(first).containsExactly("kumpel tylko dla swoich", "kumpel publicznie");
        assertThat(second.getContent().stream().map(PostResponse::content))
            .containsExactly("moj wlasny NAJSTARSZY", "obcy publicznie NAJNOWSZY");
        assertThat(second.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("pole fromFriend odroznia krag od reszty swiata")
    void fromFriendMarksTheCircle() {
        fiveMixedPosts();

        List<PostResponse> posts =
            postService.feed("ja", FeedScope.ALL, PageRequest.of(0, 20)).getContent();

        assertThat(posts).extracting(PostResponse::fromFriend)
            .containsExactly(true, true, true, false);
    }

    @Test
    @DisplayName("wejscie na adres cudzego posta dla znajomych konczy sie odmowa")
    void singlePostIsProtectedToo() {
        Post ukryty = post(kumpel, "kumpel tylko dla swoich", PostVisibility.FRIENDS, 1);
        entityManager.flush();

        // Znajomy widzi
        assertThat(postService.getOne(ukryty.getId(), "ja").content())
            .isEqualTo("kumpel tylko dla swoich");

        // Obcy nie - i nie pomoze wpisanie adresu z reki
        assertThatThrownBy(() -> postService.getOne(ukryty.getId(), "obcy"))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("nie da sie zareagowac na post, ktorego nie wolno nam zobaczyc")
    void reactionNeedsAccess() {
        Post ukryty = post(kumpel, "kumpel tylko dla swoich", PostVisibility.FRIENDS, 1);
        entityManager.flush();

        /*
         * Samo ukrycie posta na tablicy nie wystarcza: identyfikatory sa kolejnymi liczbami, wiec
         * bez tego sprawdzenia wystarczyloby wyslac PUT z pominieciem przegladarki, zeby autor
         * dostal powiadomienie od osoby, ktora nie miala prawa tego posta przeczytac.
         */
        assertThatThrownBy(() -> reactionService.set(ukryty.getId(), "obcy", ReactionType.FIRE))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThatThrownBy(() -> reactionService.authors(ukryty.getId(), "obcy"))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("profil pokazuje obcemu tylko publiczne posty - razem z licznikiem")
    void profileHidesFriendsOnlyPosts() {
        post(kumpel, "kumpel publicznie", PostVisibility.PUBLIC, 2);
        post(kumpel, "kumpel tylko dla swoich", PostVisibility.FRIENDS, 1);
        entityManager.flush();

        var forStranger = postService.byAuthor("kumpel", "obcy", PageRequest.of(0, 20));
        assertThat(forStranger.getContent()).extracting(PostResponse::content)
            .containsExactly("kumpel publicznie");

        /* Licznik w naglowku profilu musi zgadzac sie z tym, co widac nizej. */
        assertThat(publicProfileService.profile("kumpel", "obcy").postCount()).isEqualTo(1);
        assertThat(publicProfileService.profile("kumpel", "ja").postCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("regula w Javie i regula w bazie daja ten sam wynik")
    void theRuleInJavaAndInTheDatabaseAgree() {
        fiveMixedPosts();

        /*
         * "Kto moze zobaczyc post" jest zapisane dwa razy: w JPQL-u (PostRepository.findFeed - dla
         * calej tablicy) i w Javie (Post.isVisibleTo - dla pojedynczego posta).
         */
        for (String viewerName : List.of("ja", "kumpel", "obcy", "szef")) {
            User viewer = userRepository.findByUsername(viewerName).orElseThrow();

            List<String> fromDatabase = feed(viewerName, FeedScope.ALL);

            List<String> fromJava = postRepository.findAll().stream()
                .filter(p -> p.isVisibleTo(viewer))
                .map(Post::getContent)
                .toList();

            assertThat(fromDatabase)
                .as("widocznosc dla uzytkownika %s", viewerName)
                .containsExactlyInAnyOrderElementsOf(fromJava);
        }
    }

    @Test
    @DisplayName("post bez wpisanej widocznosci zachowuje sie jak publiczny")
    void missingVisibilityMeansPublic() {
        Post stary = post(obcy, "sprzed zmiany", PostVisibility.PUBLIC, 1);
        entityManager.flush();

        /*
         * Tak wygladaja wiersze sprzed dolozenia kolumny: Hibernate dokłada ja pusta, a
         * PostVisibilityMigration wypelnia je przy starcie.
         */
        entityManager.createNativeQuery(
                "UPDATE posts SET visibility = NULL WHERE id = " + stary.getId())
            .executeUpdate();
        entityManager.clear();

        assertThat(feed("ja", FeedScope.ALL)).contains("sprzed zmiany");
        assertThat(postRepository.findById(stary.getId()).orElseThrow().getVisibility())
            .isEqualTo(PostVisibility.PUBLIC);
    }
}
