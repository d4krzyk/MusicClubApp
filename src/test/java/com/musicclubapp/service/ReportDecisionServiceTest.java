package com.musicclubapp.service;

import com.musicclubapp.dto.BanRequest;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Decyzja w zgloszeniu razem z jej wykonaniem.
 *
 * <p><b>Co tu sprawdzamy, a czego nie.</b> Ta klasa niczego sama nie kasuje
 * i nikogo nie karze - <b>spina</b> zgloszenia z moderacja kont. Testujemy
 * wiec to, co do niej nalezy: czy wola wlasciwe rzeczy, we wlasciwej
 * kolejnosci i czy odmawia tam, gdzie powinna. Ze kara faktycznie zapisuje
 * sie w bazie, sprawdza {@code UserModerationServiceTest}, a caly przeplyw
 * na prawdziwej bazie - {@code ReportServiceTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Decyzja w zgloszeniu i jej wykonanie")
class ReportDecisionServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ReportService reports;
    @Mock private UserModerationService moderation;
    @Mock private PostService posts;

    @InjectMocks private ReportDecisionService decisions;

    private User user(String username) {
        return new User(username, username + "@example.com", "hash");
    }

    /** Zgloszenie na wskazane konto, opcjonalnie o konkretnym poscie. */
    private Report reportOn(User reported, Post post) {
        Report report = new Report(
            user("zglaszajacy"), reported,
            ReportReason.HARASSMENT, ReportContext.PROFILE, "opis zgloszenia");
        report.setPost(post);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));
        return report;
    }

    private ResolveReportRequest decision(ModerationAction action, Integer hours, Boolean forever) {
        return new ResolveReportRequest(
            ReportStatus.RESOLVED, "notatka administratora", action, hours, forever);
    }

    @Test
    @DisplayName("dzialanie NONE zamyka sprawe i nie rusza konta")
    void noneOnlyClosesTheCase() {
        reportOn(user("troll"), null);

        decisions.resolve("admin", 5L, decision(ModerationAction.NONE, null, null));

        /*
         * Sedno: "zasadne, ale bez kary" musi byc mozliwe do wyrazenia. Gdyby
         * zamkniecie karalo automatycznie, jedynym sposobem na niekaranie
         * byloby oddalenie zgloszenia jako bezpodstawnego - czyli zapisanie
         * w historii konta nieprawdy.
         */
        verify(reports).resolve(eq("admin"), eq(5L), any(ResolveReportRequest.class));
        verify(moderation, never()).setBan(any(), any(), any(), any());
        verify(moderation, never()).deleteUser(any(), any());
        verify(posts, never()).delete(any(), any());
    }

    @Test
    @DisplayName("zakaz ze zgloszenia trafia do moderacji z wlasciwym rodzajem kary")
    void banIsDelegatedWithItsKind() {
        reportOn(user("troll"), null);

        decisions.resolve("admin", 5L, decision(ModerationAction.BAN_MESSAGING, 24, false));

        verify(moderation).setBan(eq("admin"), any(), eq(BanKind.MESSAGING), any(BanRequest.class));
    }

    @Test
    @DisplayName("usuniecie konta trafia do moderacji")
    void accountDeletionIsDelegated() {
        reportOn(user("troll"), null);

        decisions.resolve("admin", 5L, decision(ModerationAction.DELETE_ACCOUNT, null, null));

        verify(moderation).deleteUser(eq("admin"), any());
    }

    /**
     * Kasowanie posta wymaga NAJPIERW odpiecia go od zgloszen.
     *
     * <p>Zgloszenie wskazuje na post kluczem obcym, wiec dopoki wskazuje, baza
     * posta nie odda - decyzja "usun post" konczyla sie przez to bledem 500.
     * Atrapa repozytorium kluczy obcych nie ma, wiec tutaj pilnujemy samej
     * KOLEJNOSCI; ze baza faktycznie odmawia, sprawdza {@code ReportServiceTest}
     * na prawdziwej bazie.</p>
     */
    @Test
    @DisplayName("post kasuje sie DOPIERO po odpieciu go od zgloszen")
    void postIsDetachedBeforeDeleting() {
        Post post = org.mockito.Mockito.mock(Post.class);
        given(post.getId()).willReturn(42L);
        reportOn(user("troll"), post);

        decisions.resolve("admin", 5L, decision(ModerationAction.DELETE_POST, null, null));

        var kolejnosc = org.mockito.Mockito.inOrder(reportRepository, posts);
        kolejnosc.verify(reportRepository).detachPost(42L);
        kolejnosc.verify(posts).delete(42L, "admin");
    }

    @Test
    @DisplayName("nie da sie kasowac posta przy zgloszeniu, ktore posta nie dotyczy")
    void cannotDeletePostWhenReportHasNone() {
        reportOn(user("troll"), null);

        assertThatThrownBy(() -> decisions.resolve("admin", 5L,
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
    @DisplayName("administrator NIE karze wlasnego konta przez zgloszenie")
    void adminCannotPunishSelf() {
        reportOn(user("admin"), null);

        for (ModerationAction kara : List.of(ModerationAction.BAN_POSTING,
                ModerationAction.BAN_MESSAGING, ModerationAction.DELETE_ACCOUNT)) {
            assertThatThrownBy(() -> decisions.resolve("admin", 5L, decision(kara, 24, false)))
                .describedAs("kara %s na wlasne konto", kara)
                .isInstanceOf(OperationNotAllowedException.class);
        }

        verify(reports, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("ale ZAMKNAC zgloszenie na siebie moze - bez kary")
    void adminMayCloseAReportAboutSelf() {
        reportOn(user("admin"), null);

        decisions.resolve("admin", 5L, decision(ModerationAction.NONE, null, null));

        verify(reports).resolve(eq("admin"), eq(5L), any(ResolveReportRequest.class));
    }

    @Test
    @DisplayName("zgloszenie na INNEGO administratora rozpatruje sie normalnie")
    void reportAboutAnotherAdminIsHandledNormally() {
        reportOn(user("admin2"), null);

        decisions.resolve("admin", 5L, decision(ModerationAction.BAN_POSTING, 24, false));

        verify(moderation).setBan(eq("admin"), any(), eq(BanKind.POSTING), any(BanRequest.class));
    }

    /**
     * Kolejnosc, ktora latwo przeoczyc.
     *
     * <p>Gdyby kara wykonywala sie PRZED zamknieciem sprawy, dwa klikniecia
     * pod rzad (albo dwoje administratorow naraz) nalozylyby ja dwa razy -
     * a dopiero potem wyszlo by na jaw, ze zgloszenie bylo juz zamkniete.</p>
     */
    @Test
    @DisplayName("gdy sprawa byla juz zamknieta, kara NIE wykonuje sie drugi raz")
    void doesNotPunishTwiceWhenAlreadyClosed() {
        reportOn(user("troll"), null);
        given(reports.resolve(any(), any(), any()))
            .willThrow(OperationNotAllowedException.reportAlreadyClosed());

        assertThatThrownBy(() -> decisions.resolve("admin", 5L,
            decision(ModerationAction.DELETE_ACCOUNT, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);

        verify(moderation, never()).deleteUser(any(), any());
    }

    @Test
    @DisplayName("otwartej sprawy nie da sie otworzyc drugi raz")
    void openReportCannotBeReopened() {
        reportOn(user("troll"), null);   // nowe zgloszenie jest OTWARTE

        assertThatThrownBy(() -> decisions.reopen("admin", 5L))
            .isInstanceOf(OperationNotAllowedException.class);
    }
}
