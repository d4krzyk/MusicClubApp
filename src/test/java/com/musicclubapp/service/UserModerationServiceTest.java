package com.musicclubapp.service;

import com.musicclubapp.entity.BanKind;
import com.musicclubapp.dto.BanRequest;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Uprawnienia administratora: usuwanie kont i zakaz publikowania. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Moderacja kont - usuwanie i zakaz publikowania")
class UserModerationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private com.musicclubapp.repository.ReportRepository reportRepository;
    @Mock private UserMapper userMapper;
    @Mock private NetworkService network;
    @Mock private AccountDeletionService deletion;

    @InjectMocks private UserModerationService moderationService;

    private User user(String username) {
        return new User(username, username + "@example.com", "hash");
    }

    @Test
    @DisplayName("administrator NIE MOZE usunac wlasnego konta")
    void adminCannotDeleteSelf() {
        /* To nie jest przesadna ostroznosc. */
        given(userRepository.findById(1L)).willReturn(Optional.of(user("admin")));

        assertThatThrownBy(() -> moderationService.deleteUser("admin", 1L))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(userRepository, never()).delete(any());
    }

    /**
     * Moderacja pilnuje UPRAWNIEN, a nie sprzatania.
     *
     * <p>Co dokladnie i w jakiej kolejnosci znika, sprawdza
     * {@code AccountDeletionServiceTest} - tutaj wystarczy, ze administrator
     * o to poprosil.</p>
     */
    @Test
    @DisplayName("usuniecie konta zleca skasowanie modulowi od kasowania")
    void deletingAccountDelegatesTheErasure() {
        User target = user("troll");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));

        moderationService.deleteUser("admin", 7L);

        verify(deletion).erase(target);
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

        moderationService.setBan("admin", 7L, BanKind.POSTING, new BanRequest(24, false));

        ArgumentCaptor<User> stored = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(stored.capture());

        LocalDateTime until = stored.getValue().bannedUntil(BanKind.POSTING);
        assertThat(until).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(until).isBefore(LocalDateTime.now().plusHours(25));
        assertThat(stored.getValue().isBanned(BanKind.POSTING)).isTrue();
    }

    @Test
    @DisplayName("pusta liczba godzin ZDEJMUJE zakaz")
    void nullHoursLiftsTheBan() {
        User target = new User("troll", "troll@example.com", "hash");
        target.setBannedUntil(BanKind.POSTING, LocalDateTime.now().plusDays(3));
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setBan("admin", 7L, BanKind.POSTING, new BanRequest(null, false));

        assertThat(target.bannedUntil(BanKind.POSTING)).isNull();
        assertThat(target.isBanned(BanKind.POSTING)).isFalse();
    }

    @Test
    @DisplayName("zakaz bezterminowy zapisuje termin, ktory nie nadejdzie")
    void foreverBanNeverExpires() {
        User target = new User("troll", "troll@example.com", "hash");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setBan("admin", 7L, BanKind.POSTING, new BanRequest(null, true));

        assertThat(target.bannedUntil(BanKind.POSTING)).isEqualTo(User.FOREVER);
        assertThat(target.isBanned(BanKind.POSTING)).isTrue();
        assertThat(User.isForever(target.bannedUntil(BanKind.POSTING))).isTrue();
    }

    /** Pulapka, ktora latwo zastawic na siebie samemu. */
    @Test
    @DisplayName("zakaz na zawsze NIE jest mylony ze zdjeciem zakazu, choc oba nie maja godzin")
    void foreverIsNotConfusedWithLifting() {
        User target = new User("troll", "troll@example.com", "hash");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setBan("admin", 7L, BanKind.MESSAGING, new BanRequest(null, true));
        assertThat(target.isBanned(BanKind.MESSAGING))
            .describedAs("zakaz bezterminowy ma OBOWIAZYWAC, a nie zostac zdjety")
            .isTrue();

        // A samo "bez godzin i bez na zawsze" ma nadal zdejmowac kare
        moderationService.setBan("admin", 7L, BanKind.MESSAGING, new BanRequest(null, false));
        assertThat(target.bannedUntil(BanKind.MESSAGING)).isNull();
        assertThat(target.isBanned(BanKind.MESSAGING)).isFalse();
    }

    @Test
    @DisplayName("ukarany bezterminowo widzi komunikat BEZ daty 9999")
    void foreverBanMessageHasNoAbsurdDate() {
        /* Termin 31.12.9999 jest umowny i ma nie wychodzic na wierzch. */
        OperationNotAllowedException problem =
            OperationNotAllowedException.banned(BanKind.POSTING, User.FOREVER);

        assertThat(problem.getMessageKey()).isEqualTo("error.ban.posting.forever");
        assertThat(problem.getMessage()).doesNotContain("9999");
        assertThat(problem.getArguments()).isEmpty();
    }

    @Test
    @DisplayName("zakaz z przeszlosci wygasa SAM, bez zadnego sprzatania")
    void expiredBanNeedsNoCleanup() {
        User target = new User("bylyTroll", "byly@example.com", "hash");
        target.setBannedUntil(BanKind.POSTING, LocalDateTime.now().minusMinutes(1));

        /* Nie ma tu zadnego zadania w tle ani pola "czy zablokowany" do odswiezenia. */
        assertThat(target.isBanned(BanKind.POSTING)).isFalse();
        assertThat(target.bannedUntil(BanKind.POSTING)).isNotNull();
    }

    @Test
    @DisplayName("administrator nie blokuje sam siebie")
    void adminCannotBanSelf() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user("admin")));

        assertThatThrownBy(() ->
            moderationService.setBan("admin", 1L, BanKind.POSTING, new BanRequest(24, false)))
            .isInstanceOf(OperationNotAllowedException.class);
    }
}
