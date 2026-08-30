package com.musicclubapp.service;

import com.musicclubapp.dto.NotificationResponse;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.NotificationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Powiadomienia na prawdziwej bazie. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Powiadomienia - kiedy powstaja i kiedy znikaja")
class NotificationServiceTest {

    @Autowired private NotificationService notifications;
    @Autowired private ReactionService reactionService;
    @Autowired private FriendService friendService;
    @Autowired private PostService postService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private EntityManager entityManager;

    private User ala;
    private User bob;
    private Post postAli;

    @BeforeEach
    void setUp() {
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        postAli = postRepository.save(new Post(ala, "post Ali"));
    }

    private long ile(String username) {
        return notificationRepository.countUnread(username);
    }

    @Test
    @DisplayName("reakcja na CUDZY post powiadamia autora")
    void reactionNotifiesAuthor() {
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        entityManager.flush();

        assertThat(ile("ala")).isEqualTo(1);

        NotificationResponse n = notifications.forUser("ala", PageRequest.of(0, 10))
            .getContent().get(0);
        assertThat(n.type()).isEqualTo(NotificationType.REACTION);
        assertThat(n.actorUsername()).isEqualTo("bob");
        assertThat(n.reactionType()).isEqualTo(ReactionType.FIRE);
        // Klikniecie ma prowadzic do TEGO posta, a nie na tablice
        assertThat(n.link()).isEqualTo("/post/" + postAli.getId());
        assertThat(n.postExcerpt()).isEqualTo("post Ali");
    }

    @Test
    @DisplayName("reakcja na WLASNY post nie powiadamia nikogo")
    void reactionOnOwnPostNotifiesNobody() {
        /* Wiadomo, co sie samemu zrobilo. */
        reactionService.set(postAli.getId(), "ala", ReactionType.FIRE);
        entityManager.flush();

        assertThat(ile("ala")).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("zmiana reakcji ODSWIEZA wpis zamiast dokladac drugi")
    void changingReactionRefreshesInsteadOfAdding() {
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        reactionService.set(postAli.getId(), "bob", ReactionType.MEH);
        reactionService.set(postAli.getId(), "bob", ReactionType.MID);
        entityManager.flush();

        /* Trzy klikniecia, jedno powiadomienie. */
        assertThat(ile("ala")).isEqualTo(1);
        assertThat(notifications.forUser("ala", PageRequest.of(0, 10))
            .getContent().get(0).reactionType()).isEqualTo(ReactionType.MID);
    }

    @Test
    @DisplayName("cofniecie reakcji KASUJE powiadomienie o niej")
    void undoingReactionRemovesTheNotification() {
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        entityManager.flush();
        assertThat(ile("ala")).isEqualTo(1);

        reactionService.revert(postAli.getId(), "bob");
        entityManager.flush();

        /*
         * Zostawione powiadomienie prowadziloby do posta, pod ktorym nie ma juz sladu po tej
         * reakcji - czyli do czegos, co sie "odstalo".
         */
        assertThat(ile("ala")).isZero();
    }

    @Test
    @DisplayName("dwie rozne osoby to dwa powiadomienia")
    void twoPeopleMakeTwoNotifications() {
        User cezary = userRepository.save(new User("cezary", "c@example.com", "hash"));

        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        reactionService.set(postAli.getId(), "cezary", ReactionType.FIRE);
        entityManager.flush();

        // Odswiezanie dotyczy TEJ SAMEJ osoby, nie tego samego posta
        assertThat(ile("ala")).isEqualTo(2);
        assertThat(cezary.getUsername()).isEqualTo("cezary");
    }

    @Test
    @DisplayName("zaproszenie powiadamia odbiorce i prowadzi na strone znajomych")
    void invitationNotifiesRecipient() {
        friendService.invite("bob", "ala");
        entityManager.flush();

        assertThat(ile("ala")).isEqualTo(1);
        NotificationResponse n = notifications.forUser("ala", PageRequest.of(0, 10))
            .getContent().get(0);
        assertThat(n.type()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(n.link()).isEqualTo("/znajomi");
    }

    @Test
    @DisplayName("przyjecie zaproszenia powiadamia NADAWCE i prowadzi na jego profil")
    void acceptingNotifiesTheSender() {
        friendService.invite("bob", "ala");
        entityManager.flush();

        friendService.accept(idZaproszenia(), "ala");
        entityManager.flush();

        // Bob czekal na odpowiedz - to on ma sie dowiedziec
        assertThat(ile("bob")).isEqualTo(1);
        NotificationResponse n = notifications.forUser("bob", PageRequest.of(0, 10))
            .getContent().get(0);
        assertThat(n.type()).isEqualTo(NotificationType.FRIEND_ACCEPTED);
        assertThat(n.link()).isEqualTo("/profil/ala");
    }

    @Test
    @DisplayName("odrzucone zaproszenie zabiera ze soba powiadomienie o sobie")
    void rejectedInvitationRemovesItsNotification() {
        friendService.invite("bob", "ala");
        entityManager.flush();
        assertThat(ile("ala")).isEqualTo(1);

        friendService.rejectOrCancel(idZaproszenia(), "ala");
        entityManager.flush();

        /*
         * Inaczej powiadomienie prowadziloby na strone znajomych, gdzie nic juz nie czeka - a to
         * wyglada jak usterka aplikacji.
         */
        assertThat(ile("ala")).isZero();
    }

    @Test
    @DisplayName("usuniecie posta zabiera powiadomienia o reakcjach pod nim")
    void deletingPostRemovesItsNotifications() {
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        entityManager.flush();
        assertThat(ile("ala")).isEqualTo(1);

        postService.delete(postAli.getId(), "ala");
        entityManager.flush();

        /*
         * Powiadomienie wskazuje posta KLUCZEM OBCYM, wiec gdyby nie znikalo, baza w ogole nie
         * pozwolilaby skasowac posta.
         */
        assertThat(ile("ala")).isZero();
    }

    @Test
    @DisplayName("cudzego powiadomienia nie da sie oznaczyc jako przeczytane")
    void cannotMarkSomeoneElsesNotification() {
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        entityManager.flush();

        Long id = notifications.forUser("ala", PageRequest.of(0, 10))
            .getContent().get(0).id();

        // Bob probuje wyczyscic dzwonek Ali
        notifications.markRead("bob", id);
        entityManager.flush();

        assertThat(ile("ala")).isEqualTo(1);
    }

    @Test
    @DisplayName("oznaczenie wszystkich zeruje licznik")
    void markAllReadClearsTheCounter() {
        User cezary = userRepository.save(new User("cezary", "c@example.com", "hash"));
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        reactionService.set(postAli.getId(), "cezary", ReactionType.MID);
        entityManager.flush();
        assertThat(ile("ala")).isEqualTo(2);

        assertThat(notifications.markAllRead("ala")).isEqualTo(2);
        entityManager.flush();

        assertThat(ile("ala")).isZero();
        // Same powiadomienia zostaja - znika tylko oznaczenie "nowe"
        assertThat(notifications.forUser("ala", PageRequest.of(0, 10)).getTotalElements())
            .isEqualTo(2);
        assertThat(cezary.getUsername()).isEqualTo("cezary");
    }

    /** Usuwanie pojedynczego powiadomienia. */
    @Test
    @DisplayName("wlasne powiadomienie mozna usunac, a licznik sie zmniejsza")
    void ownNotificationCanBeDeleted() {
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        entityManager.flush();
        assertThat(ile("ala")).isEqualTo(1);

        Long id = notifications.forUser("ala", PageRequest.of(0, 10))
            .getContent().get(0).id();

        notifications.delete("ala", id);
        entityManager.flush();

        assertThat(ile("ala")).isZero();
        assertThat(notifications.forUser("ala", PageRequest.of(0, 10)).getTotalElements())
            .isZero();
    }

    @Test
    @DisplayName("CUDZEGO powiadomienia nie da sie usunac")
    void cannotDeleteSomeoneElsesNotification() {
        /* Sprawdzamy odbiorce, a nie sam identyfikator. */
        reactionService.set(postAli.getId(), "bob", ReactionType.FIRE);
        entityManager.flush();

        Long id = notifications.forUser("ala", PageRequest.of(0, 10))
            .getContent().get(0).id();

        notifications.delete("bob", id);
        entityManager.flush();

        assertThat(notifications.forUser("ala", PageRequest.of(0, 10)).getTotalElements())
            .describedAs("powiadomienie Ali ma przetrwac probe usuniecia przez Bobka")
            .isEqualTo(1);
    }

    /** Identyfikator jedynego oczekujacego zaproszenia w bazie. */
    private Long idZaproszenia() {
        return entityManager
            .createQuery("SELECT f.id FROM FriendRequest f", Long.class)
            .getSingleResult();
    }
}
