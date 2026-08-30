package com.musicclubapp.service;

import com.musicclubapp.entity.FavoritePlaylist;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.MessageRepository;
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

/** Usuwanie konta na prawdziwej bazie. */
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
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageService messageService;
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
    @DisplayName("wiadomosci z czatu znikaja w OBIE strony")
    void chatMessagesGoBothWays() {
        /* Wiadomosc wskazuje na konto DWOMA kluczami obcymi - jako nadawca i jako odbiorca. */
        troll.addFriend(ala);
        userRepository.save(troll);
        userRepository.save(ala);
        entityManager.flush();

        messageService.send("troll", "ala", new com.musicclubapp.dto.SendMessageRequest(
            "od trolla", null, null, null));
        messageService.send("ala", "troll", new com.musicclubapp.dto.SendMessageRequest(
            "do trolla", null, null, null));
        entityManager.flush();

        assertThat(messageRepository.count()).isEqualTo(2);

        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();

        assertThat(messageRepository.count()).isZero();
        assertThat(userRepository.findByUsername("troll")).isEmpty();
        // Konto rozmowcy ma zostac nietkniete
        assertThat(userRepository.findByUsername("ala")).isPresent();
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

        /* Post ma zostac. */
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
         * Gdyby zostala druga polowa wiersza, Ala nadal "mialaby znajomego", ktorego konto juz nie
         * istnieje - a kazde wyswietlenie jej listy konczyloby sie bledem.
         */
        assertThat(userRepository.countFriends("ala")).isZero();
    }

    @Test
    @DisplayName("gablotka playlist znika razem z kontem")
    void playlistShowcaseIsRemoved() {
        /*
         * Wiersze gablotki wskazuja na konto kluczem obcym, a encja User nic o nich nie wie -
         * kaskada ich nie zabierze.
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
        /* Wiersz w tabeli artists jest WSPOLNY dla wszystkich. */
        moderationService.deleteUser("admin", troll.getId());
        entityManager.flush();

        assertThat(userRepository.findByUsername("troll")).isEmpty();
        assertThat(userRepository.findByUsername("ala")).isPresent();
    }

    @Test
    @DisplayName("da sie usunac post, pod ktorym sa CUDZE reakcje")
    void postWithSomeoneElsesReactionsCanBeDeleted() {
        /* Wyglada na oczywiste, a przez dlugi czas nie dzialalo. */
        Post post = postRepository.save(new Post(ala, "post z reakcjami"));
        reactionService.set(post.getId(), "troll", ReactionType.FIRE);
        entityManager.flush();

        postService.delete(post.getId(), "ala");
        entityManager.flush();

        assertThat(postRepository.findById(post.getId())).isEmpty();
    }
}
