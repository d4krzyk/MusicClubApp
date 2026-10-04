package com.musicclubapp.service;

import com.musicclubapp.entity.User;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Kolejnosc kasowania i haslo jako zabezpieczenie.
 *
 * <p>Ta klasa niczego sama nie kasuje - zna KOLEJNOSC, w jakiej trzeba
 * poprosic moduly. Dlatego testujemy kolejnosc, a nie znikanie wierszy;
 * ze baza faktycznie odmawia przy zlej kolejnosci, sprawdza
 * {@code UserDeletionTest} na prawdziwej bazie.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Kasowanie konta i wlasnych postow")
class AccountDeletionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notifications;
    @Mock private ReactionService reactions;
    @Mock private ReportService reports;
    @Mock private PostService posts;
    @Mock private FriendService friends;
    @Mock private MessageService messages;
    @Mock private NetworkService network;
    @Mock private PlaylistService playlists;
    @Mock private EventParticipationService eventParticipations;
    @Mock private EmailVerificationService emailVerification;
    @Mock private BlockService blocks;
    @Mock private UserService users;
    @Mock private PushService push;
    @Mock private ClanService clans;
    @Mock private CommentRepository comments;
    @Mock private DiscoverService discover;
    @Mock private ProfileCardService cards;

    @InjectMocks private AccountDeletionService deletion;

    private User konto(String username) {
        return new User(username, username + "@example.com", "hash");
    }

    private User zalogowany(String username, boolean hasloPasuje) {
        User user = konto(username);
        given(userRepository.findByUsername(username)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(hasloPasuje);
        return user;
    }

    /**
     * Kolejnosc, ktorej nie widac po nazwach metod.
     *
     * <p>Powiadomienia i zgloszenia wskazuja na posty kluczem obcym, wiec
     * musza pojsc PRZED nimi; samo konto - na koncu, gdy nic juz na nie nie
     * wskazuje.</p>
     */
    @Test
    @DisplayName("kasowanie konta prosi moduly we WLASCIWEJ kolejnosci")
    void erasingAsksEveryModuleInOrder() {
        User target = konto("troll");

        deletion.erase(target);

        InOrder kolejnosc = inOrder(notifications, comments, reactions, reports, clans, posts, friends,
            messages, network, playlists, eventParticipations, emailVerification, blocks, push, users, userRepository, discover, cards);

        kolejnosc.verify(notifications).deleteAllOf(target.getId());
        kolejnosc.verify(comments).deleteByAuthorId(target.getId());
        kolejnosc.verify(reactions).deleteAllOf(target.getId());
        kolejnosc.verify(reports).deleteAllOf(target.getId());
        kolejnosc.verify(clans).deleteAllOf(target);
        kolejnosc.verify(posts).deleteAllOf(target.getId());
        kolejnosc.verify(friends).deleteAllOf(target);
        kolejnosc.verify(messages).deleteAllOf(target.getId());
        kolejnosc.verify(network).deleteAllOf(target.getId());
        kolejnosc.verify(playlists).deleteAllOf(target.getId());
        kolejnosc.verify(eventParticipations).deleteAllOf(target.getId());
        kolejnosc.verify(emailVerification).deleteAllOf(target.getId());
        kolejnosc.verify(blocks).deleteAllOf(target.getId());
        kolejnosc.verify(push).deleteAllOf(target.getId());
        kolejnosc.verify(users).deleteAvatarOf(target);
        kolejnosc.verify(discover).deleteAllOf(target);
        kolejnosc.verify(cards).clear(target);
        kolejnosc.verify(userRepository).delete(target);
    }

    /**
     * Kasowanie samych postow ma te sama pulapke co kasowanie konta.
     *
     * <p>Zgloszenia ODPINAMY, a nie kasujemy - sa historia konta
     * zglaszajacego, a tresc posta zostaje w migawce dowodow.</p>
     */
    @Test
    @DisplayName("kasowanie wlasnych postow odpina je najpierw od powiadomien i zgloszen")
    void erasingPostsDetachesWhatPointsAtThem() {
        User owner = zalogowany("ala", true);

        deletion.deleteOwnPosts("ala", "TajneHaslo1");

        InOrder kolejnosc = inOrder(notifications, reports, posts);
        kolejnosc.verify(notifications).postsOfAuthorDeleted(owner.getId());
        kolejnosc.verify(reports).detachPostsOf(owner.getId());
        kolejnosc.verify(posts).deleteAllOf(owner.getId());
    }

    @Test
    @DisplayName("kasowanie wlasnych postow NIE rusza konta")
    void erasingPostsLeavesTheAccount() {
        zalogowany("ala", true);

        deletion.deleteOwnPosts("ala", "TajneHaslo1");

        verify(userRepository, never()).delete(any());
        verify(friends, never()).deleteAllOf(any());
        verify(messages, never()).deleteAllOf(any());
    }

    @Test
    @DisplayName("wlasne konto kasuje sie po podaniu hasla")
    void ownAccountIsDeletedAfterPasswordCheck() {
        User user = zalogowany("ala", true);

        deletion.deleteOwnAccount("ala", "TajneHaslo1");

        verify(userRepository).delete(user);
    }

    /**
     * Haslo nie jest formalnoscia.
     *
     * <p>Sesja moze zostac otwarta na cudzym komputerze, a tej operacji nie
     * da sie cofnac - dlatego pytamy o haslo, a nie o samo potwierdzenie.</p>
     */
    @Test
    @DisplayName("zle haslo NIE kasuje konta")
    void wrongPasswordDeletesNothing() {
        zalogowany("ala", false);

        assertThatThrownBy(() -> deletion.deleteOwnAccount("ala", "zgadywane"))
            .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(userRepository, never()).delete(any());
        verify(notifications, never()).deleteAllOf(any());
    }

    @Test
    @DisplayName("zle haslo NIE kasuje postow")
    void wrongPasswordKeepsThePosts() {
        zalogowany("ala", false);

        assertThatThrownBy(() -> deletion.deleteOwnPosts("ala", "zgadywane"))
            .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(posts, never()).deleteAllOf(any());
    }
}
