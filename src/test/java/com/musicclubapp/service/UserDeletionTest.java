package com.musicclubapp.service;

import com.musicclubapp.entity.FavoritePlaylist;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Usuwanie konta <b>na prawdziwej bazie</b>.
 *
 * <p><b>Po co, skoro jest juz {@code UserModerationServiceTest}?</b> Tamten
 * pracuje na atrapach i sprawdza, ze serwis <i>wola</i> odpowiednie metody.
 * Nie ma jednak zadnej mozliwosci zauwazyc, ze wywolanie o jedna tabele za
 * malo konczy sie odmowa bazy z powodu klucza obcego - bo atrapa zgodzi sie
 * na wszystko.</p>
 *
 * <p>Najwazniejszy jest tu {@link #friendshipIsRemovedOnBothSides()}: wiersz
 * znajomosci powstaje w obie strony, a przy kasowaniu konta Hibernate sam
 * sprzata tylko jedna z nich.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Usuwanie konta - sprzatanie powiazanych wierszy")
class UserDeletionTest {

    @Autowired private UserModerationService moderationService;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ReactionRepository reactionRepository;
    @Autowired private FriendRequestRepository requestRepository;
    @Autowired private FavoritePlaylistRepository playlistRepository;
    @Autowired private ReactionService reactionService;
    @Autowired private PostService postService;
    @Autowired private FriendService friendService;
    @Autowired private EntityManager entityManager;

    private User troll;
    private User ala;

    @BeforeEach
    void setUp() {
        troll = userRepository.save(new User("troll", "troll@example.com", "hash"));
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
    }

    @Test
    @DisplayName("konto znika razem z wlasnymi postami")
    void ownPostsAreDeleted() {
        postRepository.save(new Post(troll, "post trolla"));
        postRepository.save(new Post(ala, "post Ali"));

        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();

        assertThat(postRepository.countByAuthorUsername("ala")).isEqualTo(1);
        assertThat(userRepository.findByUsername("troll")).isEmpty();
    }

    @Test
    @DisplayName("reakcje pod CUDZYMI postami znikaja, ale same posty zostaja")
    void reactionsOnOtherPeoplesPostsAreRemoved() {
        Post postAli = postRepository.save(new Post(ala, "post Ali"));
        reactionService.set(postAli.getId(), "troll", ReactionType.FIRE);
        entityManager.flush();

        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();
        entityManager.clear();

        /*
         * Post ma zostac. Usuwamy konto, a nie cudze tresci - inaczej
         * skasowanie jednego trolla zabieraloby fragmenty cudzych rozmow.
         */
        assertThat(postRepository.findById(postAli.getId())).isPresent();
        assertThat(reactionRepository.find(postAli.getId(), "troll")).isEmpty();
    }

    @Test
    @DisplayName("znajomosc znika po OBU stronach")
    void friendshipIsRemovedOnBothSides() {
        friendService.invite("troll", "ala");
        friendService.invite("ala", "troll");   // wzajemne zaproszenie laczy od razu
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.countFriends("ala")).isEqualTo(1);

        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();
        entityManager.clear();

        /*
         * Gdyby zostala druga polowa wiersza, Ala nadal "mialaby znajomego",
         * ktorego konto juz nie istnieje - a kazde wyswietlenie jej listy
         * konczyloby sie bledem.
         */
        assertThat(userRepository.countFriends("ala")).isZero();
    }

    @Test
    @DisplayName("gablotka playlist znika razem z kontem")
    void playlistShowcaseIsRemoved() {
        /*
         * Wiersze gablotki wskazuja na konto kluczem obcym, a encja User
         * nic o nich nie wie - kaskada ich nie zabierze. Gdyby zostaly,
         * baza po prostu odmowilaby skasowania konta.
         *
         * Wiersz zakladamy WPROST, a nie przez PlaylistService: tamten
         * poszedlby do Spotify po tytul playlisty, czyli test zalezalby od
         * cudzego serwera. Tu chodzi wylacznie o sprzatanie przy usuwaniu.
         */
        playlistRepository.save(new FavoritePlaylist(
            troll, MusicProvider.SPOTIFY, "37i9dQZF1DXcBWIGoYBM5M", "Skladanka", null, 0));
        entityManager.flush();

        assertThat(playlistRepository.countByOwnerUsername("troll")).isEqualTo(1);

        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(playlistRepository.findAll()).isEmpty();
        assertThat(userRepository.findByUsername("troll")).isEmpty();
    }

    @Test
    @DisplayName("zaproszenia znikaja niezaleznie od tego, kto je wyslal")
    void invitationsAreRemovedInBothDirections() {
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));

        friendService.invite("troll", "ala");    // wyslane przez trolla
        friendService.invite("bob", "troll");    // wyslane DO trolla
        entityManager.flush();

        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(requestRepository.countByRecipientUsername("ala")).isZero();
        assertThat(requestRepository.findAll()).isEmpty();
        assertThat(userRepository.findByUsername("bob")).isPresent();
    }

    @Test
    @DisplayName("ulubieni znikaja razem z kontem, ale artysci w katalogu zostaja")
    void favoritesAreUnlinkedNotDeleted() {
        /*
         * Wiersz w tabeli artists jest WSPOLNY dla wszystkich. Skasowanie
         * konta ma zdjac powiazanie, a nie zabrac wykonawce reszcie
         * uzytkownikow - to ta sama zasada co przy usuwaniu z ulubionych.
         */
        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();

        assertThat(userRepository.findByUsername("troll")).isEmpty();
        assertThat(userRepository.findByUsername("ala")).isPresent();
    }

    @Test
    @DisplayName("da sie usunac post, pod ktorym sa CUDZE reakcje")
    void postWithSomeoneElsesReactionsCanBeDeleted() {
        /*
         * Wyglada na oczywiste, a przez dlugi czas nie dzialalo. Encja posta
         * ma cascade = ALL na reakcjach, wiec wydawalo sie, ze temat jest
         * zalatwiony - tyle ze kaskada opiera sie na kolekcji zaladowanej do
         * pamieci. Reakcja dopisana w tej samej transakcji do niej nie trafia
         * i baza odrzucala skasowanie posta z powodu klucza obcego.
         *
         * Testy na atrapach nie mialy szans tego zobaczyc: atrapa repozytorium
         * zgadza sie na wszystko. Wychodzi to dopiero na prawdziwej bazie.
         */
        Post post = postRepository.save(new Post(ala, "post z reakcjami"));
        reactionService.set(post.getId(), "troll", ReactionType.FIRE);
        entityManager.flush();

        postService.delete(post.getId(), "ala");
        entityManager.flush();

        assertThat(postRepository.findById(post.getId())).isEmpty();
    }
}
