package com.musicclubapp.service;

import com.musicclubapp.dto.PostingBanRequest;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.FriendRequestRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Uprawnienia administratora: usuwanie kont i zakaz publikowania.
 *
 * <p>Testujemy tu <b>decyzje</b>, a nie zapis w bazie - dlatego atrapy.
 * To, ze usuniecie konta faktycznie sprzata wszystkie powiazane wiersze,
 * sprawdza {@code UserDeletionTest} na prawdziwej bazie: jedno i drugie
 * jest potrzebne, bo blad moze siedziec albo w regule, albo w SQL-u.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Moderacja kont - usuwanie i zakaz publikowania")
class UserModerationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private ReactionRepository reactionRepository;
    @Mock private FriendRequestRepository requestRepository;
    @Mock private FavoritePlaylistRepository playlistRepository;
    @Mock private FileStorageService fileStorage;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notifications;

    @InjectMocks private UserModerationService moderationService;

    @BeforeEach
    void setUp() {
        // Domyslnie: konto bez postow. Test, ktory potrzebuje postow,
        // podmienia to u siebie.
        given(postRepository.findByAuthorId(any())).willReturn(List.of());
    }

    private User user(String username) {
        return new User(username, username + "@example.com", "hash");
    }

    @Test
    @DisplayName("administrator NIE MOZE usunac wlasnego konta")
    void adminCannotDeleteSelf() {
        /*
         * To nie jest przesadna ostroznosc. Bez tej blokady jedno klikniecie
         * moze zostawic portal bez nikogo, kto ma do niego dostep - a konta
         * administratora nie da sie potem odzyskac inaczej niz recznie
         * w bazie. Ta sama mysl co przy zmianie wlasnej roli.
         */
        given(userRepository.findById(1L)).willReturn(Optional.of(user("admin")));

        assertThatThrownBy(() -> moderationService.deleteUser("admin", 1L))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("usuniecie konta kasuje TEZ reakcje, zaproszenia i znajomosci")
    void deletingAccountCleansUpEverything() {
        User target = user("troll");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));

        moderationService.deleteUser("admin", 7L);

        /*
         * Kazde z tych wywolan odpowiada innej tabeli wskazujacej na konto.
         * Pominiecie ktoregokolwiek konczy sie tym, ze baza odmawia usuniecia
         * z powodu klucza obcego - albo, gorzej, zostawia wiersz wskazujacy
         * na uzytkownika, ktorego juz nie ma.
         */
        verify(reactionRepository).deleteByUserId(target.getId());
        verify(requestRepository).deleteBySenderIdOrRecipientId(target.getId(), target.getId());
        verify(userRepository).removeFriendshipsWith(target.getId());
        verify(userRepository).delete(target);
    }

    @Test
    @DisplayName("usuniecie konta zdejmuje z dysku awatar i zdjecia z postow")
    void deletingAccountRemovesFiles() {
        User target = new User("troll", "troll@example.com", "hash");
        target.setAvatarFileName("awatar.jpg");

        Post post = new Post(target, "post ze zdjeciem");
        post.addImage(new PostImage("zdjecie.jpg"));

        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(postRepository.findByAuthorId(any())).willReturn(List.of(post));

        moderationService.deleteUser("admin", 7L);

        // Konto usuniete z bazy, ale ze zdjeciami dalej na serwerze,
        // to usuniecie tylko na niby
        verify(fileStorage).remove("awatar.jpg");
        verify(fileStorage).remove("zdjecie.jpg");
    }

    @Test
    @DisplayName("usuniecie nieistniejacego konta konczy sie 404, a nie cicha zgoda")
    void deletingUnknownAccountThrows() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> moderationService.deleteUser("admin", 99L))
            .isInstanceOf(NoSuchElementFoundException.class);
    }

    @Test
    @DisplayName("zakaz na 24 godziny zapisuje termin w przyszlosci")
    void banSetsFutureDeadline() {
        User target = new User("troll", "troll@example.com", "hash");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setPostingBan("admin", 7L, new PostingBanRequest(24));

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());

        LocalDateTime until = stored.getValue().getPostingBannedUntil();
        assertThat(until).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(until).isBefore(LocalDateTime.now().plusHours(25));
        assertThat(stored.getValue().isPostingBanned()).isTrue();
    }

    @Test
    @DisplayName("pusta liczba godzin ZDEJMUJE zakaz")
    void nullHoursLiftsTheBan() {
        User target = new User("troll", "troll@example.com", "hash");
        target.setPostingBannedUntil(LocalDateTime.now().plusDays(3));
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setPostingBan("admin", 7L, new PostingBanRequest(null));

        assertThat(target.getPostingBannedUntil()).isNull();
        assertThat(target.isPostingBanned()).isFalse();
    }

    @Test
    @DisplayName("zakaz z przeszlosci wygasa SAM, bez zadnego sprzatania")
    void expiredBanNeedsNoCleanup() {
        User target = new User("bylyTroll", "byly@example.com", "hash");
        target.setPostingBannedUntil(LocalDateTime.now().minusMinutes(1));

        /*
         * Nie ma tu zadnego zadania w tle ani pola "czy zablokowany" do
         * odswiezenia. Liczy sie wylacznie porownanie z biezaca chwila,
         * wiec kara konczy sie dokladnie wtedy, kiedy miala sie skonczyc.
         * Wpis zostaje - administrator widzi w panelu, ze ktos byl karany.
         */
        assertThat(target.isPostingBanned()).isFalse();
        assertThat(target.getPostingBannedUntil()).isNotNull();
    }

    @Test
    @DisplayName("administrator nie blokuje sam siebie")
    void adminCannotBanSelf() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user("admin")));

        assertThatThrownBy(() ->
            moderationService.setPostingBan("admin", 1L, new PostingBanRequest(24)))
            .isInstanceOf(OperationNotAllowedException.class);
    }
}
