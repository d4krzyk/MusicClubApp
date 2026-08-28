package com.musicclubapp.service;

import com.musicclubapp.dto.MessagingBanRequest;
import com.musicclubapp.dto.PostingBanRequest;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostImage;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;
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
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private MessageService messages;
    @Mock private ReportService reports;
    @Mock private NetworkService network;
    @Mock private com.musicclubapp.repository.ReportRepository reportRepository;
    @Mock private PostService postService;

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
        verify(messages).deleteAllOf(target.getId());
        verify(reports).deleteAllOf(target.getId());
        verify(network).forgetUser(target.getId());
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

        moderationService.setPostingBan("admin", 7L, new PostingBanRequest(24, false));

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

        moderationService.setPostingBan("admin", 7L, new PostingBanRequest(null, false));

        assertThat(target.getPostingBannedUntil()).isNull();
        assertThat(target.isPostingBanned()).isFalse();
    }

    /* ------------------------------------------------------------------ */
    /*  Decyzja w zgloszeniu razem z dzialaniem                            */
    /* ------------------------------------------------------------------ */

    /**
     * Zgloszenie na konto "troll", opcjonalnie o konkretnym poscie.
     *
     * <p>Identyfikatorow nie ustawiamy, bo encje nadaja je dopiero przy
     * zapisie do bazy - w tescie na atrapach zostaja puste. Dlatego
     * wyszukiwanie konta dopasowujemy przez {@code any()}, a nie przez
     * konkretna liczbe: sprawdzamy tu <b>co sie dzieje</b>, a nie pod jakim
     * numerem.</p>
     */
    private Report reportOn(User troll, Post post) {
        Report report = new Report(
            user("zglaszajacy"), troll,
            ReportReason.HARASSMENT, ReportContext.PROFILE, "opis zgloszenia");
        report.setPost(post);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(troll));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));
        return report;
    }

    private ResolveReportRequest decision(ModerationAction action, Integer hours, Boolean forever) {
        return new ResolveReportRequest(
            ReportStatus.RESOLVED, "notatka administratora", action, hours, forever);
    }

    @Test
    @DisplayName("zamkniecie z dzialaniem NONE nie rusza konta")
    void resolvingWithoutActionChangesNothing() {
        User troll = user("troll");
        reportOn(troll, null);

        moderationService.resolveReport("admin", 5L, decision(ModerationAction.NONE, null, null));

        /*
         * Sedno: "zasadne, ale bez kary" musi byc mozliwe do wyrazenia.
         * Gdyby zamkniecie karalo automatycznie, jedynym sposobem na
         * niekaranie byloby oddalenie zgloszenia jako bezpodstawnego -
         * czyli zapisanie w historii konta nieprawdy.
         */
        assertThat(troll.isPostingBanned()).isFalse();
        assertThat(troll.isMessagingBanned()).isFalse();
        verify(userRepository, never()).delete(any(User.class));
        verify(postService, never()).delete(any(), any());
        verify(reports).resolve(eq("admin"), eq(5L), any(ResolveReportRequest.class));
    }

    @Test
    @DisplayName("zamkniecie z zakazem publikowania od razu naklada kare")
    void resolvingCanBanPosting() {
        User troll = user("troll");
        reportOn(troll, null);

        moderationService.resolveReport("admin", 5L,
            decision(ModerationAction.BAN_POSTING, 24, false));

        assertThat(troll.isPostingBanned()).isTrue();
        assertThat(troll.isMessagingBanned())
            .describedAs("zakaz publikowania to OSOBNA kara od zakazu wiadomosci")
            .isFalse();
    }

    @Test
    @DisplayName("zamkniecie moze nalozyc zakaz BEZTERMINOWY")
    void resolvingCanBanForever() {
        User troll = user("troll");
        reportOn(troll, null);

        moderationService.resolveReport("admin", 5L,
            decision(ModerationAction.BAN_MESSAGING, null, true));

        assertThat(troll.getMessagingBannedUntil()).isEqualTo(User.FOREVER);
    }

    @Test
    @DisplayName("zamkniecie z usunieciem konta faktycznie je kasuje")
    void resolvingCanDeleteTheAccount() {
        User troll = user("troll");
        reportOn(troll, null);

        moderationService.resolveReport("admin", 5L,
            decision(ModerationAction.DELETE_ACCOUNT, null, null));

        verify(userRepository).delete(troll);
    }

    @Test
    @DisplayName("zamkniecie z usunieciem posta kasuje TEN post")
    void resolvingCanDeleteTheReportedPost() {
        User troll = user("troll");
        /*
         * Post jako atrapa wylacznie po to, zeby mial identyfikator -
         * prawdziwa encja dostaje go dopiero przy zapisie do bazy,
         * a tu chodzi o sprawdzenie, ze kasujemy WLASCIWY post.
         */
        Post post = org.mockito.Mockito.mock(Post.class);
        given(post.getId()).willReturn(42L);
        reportOn(troll, post);

        moderationService.resolveReport("admin", 5L,
            decision(ModerationAction.DELETE_POST, null, null));

        // Przez PostService, a nie repozytorium - tam siedzi kasowanie zdjec z dysku
        verify(postService).delete(42L, "admin");
    }

    /**
     * Sprawdzenie kolejnosci, ktore latwo przeoczyc.
     *
     * <p>Gdyby kara wykonywala sie PRZED zamknieciem sprawy, dwa klikniecia
     * pod rzad (albo dwoje administratorow naraz) nalozylyby ja dwa razy -
     * a dopiero potem wyszlo by na jaw, ze zgloszenie bylo juz zamkniete.</p>
     */
    @Test
    @DisplayName("gdy zgloszenie bylo juz zamkniete, kara NIE wykonuje sie drugi raz")
    void doesNotPunishTwiceWhenAlreadyClosed() {
        User troll = user("troll");
        reportOn(troll, null);
        given(reports.resolve(any(), any(), any()))
            .willThrow(OperationNotAllowedException.reportAlreadyClosed());

        assertThatThrownBy(() -> moderationService.resolveReport("admin", 5L,
            decision(ModerationAction.DELETE_ACCOUNT, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("nie da sie kasowac posta przy zgloszeniu, ktore posta nie dotyczy")
    void cannotDeletePostWhenReportHasNone() {
        User troll = user("troll");
        reportOn(troll, null);

        assertThatThrownBy(() -> moderationService.resolveReport("admin", 5L,
            decision(ModerationAction.DELETE_POST, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        /*
         * Sprawa ma zostac OTWARTA. Odmowa po zamknieciu byla by najgorsza
         * z mozliwosci: zgloszenie zamkniete z notatka "post usuniety",
         * a post na miejscu - i nie da sie tego cofnac.
         */
        verify(reports, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("zakaz bezterminowy zapisuje termin, ktory nie nadejdzie")
    void foreverBanNeverExpires() {
        User target = new User("troll", "troll@example.com", "hash");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setPostingBan("admin", 7L, new PostingBanRequest(null, true));

        assertThat(target.getPostingBannedUntil()).isEqualTo(User.FOREVER);
        assertThat(target.isPostingBanned()).isTrue();
        assertThat(User.isForever(target.getPostingBannedUntil())).isTrue();
    }

    /**
     * Pulapka, ktora latwo zastawic na siebie samemu.
     *
     * <p>Zakaz bezterminowy przychodzi <b>bez</b> liczby godzin - dokladnie
     * tak samo jak polecenie "zdejmij zakaz". Gdyby serwer sprawdzal najpierw
     * {@code hours == null}, klikniecie "na zawsze" <b>zdejmowaloby</b> kare
     * zamiast ja nakladac, czyli robiloby doslownie odwrotnosc decyzji
     * administratora. Zaden z pozostalych testow by tego nie zauwazyl,
     * bo kazdy z nich podaje albo godziny, albo nic.</p>
     */
    @Test
    @DisplayName("zakaz na zawsze NIE jest mylony ze zdjeciem zakazu, choc oba nie maja godzin")
    void foreverIsNotConfusedWithLifting() {
        User target = new User("troll", "troll@example.com", "hash");
        given(userRepository.findById(7L)).willReturn(Optional.of(target));
        given(userRepository.save(any(User.class))).willAnswer(w -> w.getArgument(0));

        moderationService.setMessagingBan("admin", 7L, new MessagingBanRequest(null, true));
        assertThat(target.isMessagingBanned())
            .describedAs("zakaz bezterminowy ma OBOWIAZYWAC, a nie zostac zdjety")
            .isTrue();

        // A samo "bez godzin i bez na zawsze" ma nadal zdejmowac kare
        moderationService.setMessagingBan("admin", 7L, new MessagingBanRequest(null, false));
        assertThat(target.getMessagingBannedUntil()).isNull();
        assertThat(target.isMessagingBanned()).isFalse();
    }

    @Test
    @DisplayName("ukarany bezterminowo widzi komunikat BEZ daty 9999")
    void foreverBanMessageHasNoAbsurdDate() {
        /*
         * Termin 31.12.9999 jest umowny i ma nie wychodzic na wierzch.
         * Komunikat "zakaz do 31.12.9999" wyglada jak usterka aplikacji,
         * a nie jak decyzja administratora.
         */
        OperationNotAllowedException problem =
            OperationNotAllowedException.postingBanned(User.FOREVER);

        assertThat(problem.getMessageKey()).isEqualTo("error.post.banned.forever");
        assertThat(problem.getMessage()).doesNotContain("9999");
        assertThat(problem.getArguments()).isEmpty();
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
            moderationService.setPostingBan("admin", 1L, new PostingBanRequest(24, false)))
            .isInstanceOf(OperationNotAllowedException.class);
    }
}
