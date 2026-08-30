package com.musicclubapp.service;

import com.musicclubapp.entity.BanKind;
import com.musicclubapp.dto.CreateReportRequest;
import com.musicclubapp.dto.EvidenceLineResponse;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.dto.SendMessageRequest;
import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** Zgloszenia: limity, dowody i decyzje administratora. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Zgloszenia uzytkownikow")
class ReportServiceTest {

    @Autowired private ReportService reports;
    @Autowired private ReportDecisionService decisions;
    @Autowired private MessageService messages;
    @Autowired private ReportRepository reportRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private EntityManager entityManager;

    /** Nie chcemy czekac na oEmbed przy wiadomosci z nagraniem - patrz MessageServiceTest. */
    @MockBean private MusicMetadataService musicMetadata;

    private User ala;
    private User troll;

    /** Ilu administratorow ma serwis. */
    private int adminCount;

    @BeforeEach
    void setUp() {
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        troll = userRepository.save(new User("troll", "troll@example.com", "hash"));
        adminCount = userRepository.findByRole(Role.ADMIN).size();

        given(musicMetadata.fetch(any()))
            .willReturn(new MusicMetadataService.Metadata(null, null));

        entityManager.flush();
    }

    private CreateReportRequest profileReport() {
        return new CreateReportRequest(
            ReportReason.HARASSMENT, ReportContext.PROFILE, null,
            "Wyzywa ludzi w opisie profilu");
    }

    /* ------------------------------------------------------------------ */
    /*  Skladanie                                                          */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("zgloszenie profilu trafia do panelu jako otwarte")
    void profileReportLandsOpen() {
        ReportResponse created = reports.create("ala", "troll", profileReport());

        assertThat(created.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(created.reporterUsername()).isEqualTo("ala");
        assertThat(created.reportedUsername()).isEqualTo("troll");
        assertThat(created.reason()).isEqualTo(ReportReason.HARASSMENT);
        // Profil jest sam dla siebie dowodem - administrator moze go otworzyc
        assertThat(created.evidence()).isEmpty();
    }

    @Test
    @DisplayName("nie mozna zglosic samego siebie")
    void cannotReportSelf() {
        assertThatThrownBy(() -> reports.create("ala", "ala", profileReport()))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("samego siebie");
    }

    @Test
    @DisplayName("administratorzy dostaja powiadomienie o nowym zgloszeniu")
    void adminsGetNotified() {
        long before = notificationRepository.count();

        reports.create("ala", "troll", profileReport());

        assertThat(adminCount).isPositive();
        assertThat(notificationRepository.count()).isEqualTo(before + adminCount);
    }

    @Test
    @DisplayName("administrator NIE dostaje powiadomienia o wlasnym zgloszeniu")
    void reporterAdminIsNotNotified() {
        /*
         * Ta sama regula co wszedzie indziej przy powiadomieniach ("nie powiadamiam samego
         * siebie"), tylko zastosowana do listy odbiorcow.
         */
        long before = notificationRepository.count();

        reports.create("admin", "troll", profileReport());

        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    /* ------------------------------------------------------------------ */
    /*  Limity                                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("drugie zgloszenie na te sama osobe jest odrzucane, dopoki pierwsze czeka")
    void refusesSecondOpenReportOnSamePerson() {
        reports.create("ala", "troll", profileReport());

        assertThatThrownBy(() -> reports.create("ala", "troll", profileReport()))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("juz czeka");

        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("po ZAMKNIECIU mozna zglosic te osobe ponownie")
    void allowsReportAgainAfterClosing() {
        /* Blokada dotyczy zgloszen OTWARTYCH, a nie osoby na zawsze. */
        ReportResponse first = reports.create("ala", "troll", profileReport());
        reports.resolve("admin", first.id(),
            new ResolveReportRequest(ReportStatus.RESOLVED, "Nalozono zakaz publikowania", null, null, null));

        assertThat(reports.create("ala", "troll", profileReport()).status())
            .isEqualTo(ReportStatus.OPEN);
    }

    @Test
    @DisplayName("dzienny limit zatrzymuje zglaszanie wszystkich po kolei")
    void dailyLimitStopsMassReporting() {
        /*
         * Blokada "jedno otwarte na osobe" nie zadzialalaby tu ANI RAZU - kazde zgloszenie dotyczy
         * kogos innego.
         */
        for (int i = 0; i < ReportService.MAX_PER_DAY; i++) {
            User target = userRepository.save(
                new User("obcy" + i, "obcy" + i + "@example.com", "hash"));
            reports.create("ala", target.getUsername(), profileReport());
        }
        entityManager.flush();

        assertThatThrownBy(() -> reports.create("ala", "troll", profileReport()))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("limit");
    }

    /* ------------------------------------------------------------------ */
    /*  Dowody                                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("zgloszenie posta dolacza jego tresc jako dowod")
    void postReportCarriesContent() {
        Post post = postRepository.save(new Post(troll, "obrazliwy wpis"));
        entityManager.flush();

        ReportResponse created = reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.HATE, ReportContext.POST, post.getId(), "Obraza calej grupy ludzi"));

        assertThat(created.postId()).isEqualTo(post.getId());
        assertThat(created.evidence())
            .extracting(EvidenceLineResponse::author, EvidenceLineResponse::text)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("troll", "obrazliwy wpis"));
    }

    @Test
    @DisplayName("nie da sie podpiac pod zgloszenie posta NAPISANEGO PRZEZ KOGOS INNEGO")
    void postMustBelongToTheReportedPerson() {
        /*
         * Bez tego sprawdzenia dalo by sie zglosic Trolla, dolaczajac jako dowod cudzy - na
         * przyklad wlasny - post.
         */
        Post cudzy = postRepository.save(new Post(ala, "moj wlasny post"));
        entityManager.flush();

        assertThatThrownBy(() -> reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.SPAM, ReportContext.POST, cudzy.getId(), "Podkladam cudzy post")))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("nie nalezy");
    }

    @Test
    @DisplayName("nie da sie zglosic posta, ktorego sie nie widzi")
    void cannotReportInvisiblePost() {
        /* Post "tylko dla znajomych" osoby, ktora znajomym nie jest, jest dla nas niewidoczny. */
        Post prywatny = new Post(troll, "tylko dla znajomych");
        prywatny.setVisibility(PostVisibility.FRIENDS);
        postRepository.save(prywatny);
        entityManager.flush();

        assertThatThrownBy(() -> reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.HATE, ReportContext.POST, prywatny.getId(), "Cos tam napisal")))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("zgloszenie rozmowy dolacza wiadomosci OD NAJSTARSZEJ")
    void conversationEvidenceReadsLikeAConversation() {
        makeFriends();

        messages.send("ala", "troll", text("czesc"));
        messages.send("troll", "ala", text("odczep sie"));
        messages.send("ala", "troll", text("prosze przestan"));
        entityManager.flush();

        ReportResponse created = reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.HARASSMENT, ReportContext.CONVERSATION, null,
            "Odzywa sie do mnie w ten sposob od tygodnia"));

        assertThat(created.evidence())
            .extracting(EvidenceLineResponse::text)
            // Kolejnosc ekranu, a nie zapytania - inaczej administrator
            // czytalby rozmowe od konca
            .containsExactly("czesc", "odczep sie", "prosze przestan");
    }

    @Test
    @DisplayName("nie ma rozmowy - nie ma czego dolaczyc")
    void refusesEmptyConversationReport() {
        makeFriends();

        assertThatThrownBy(() -> reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.HARASSMENT, ReportContext.CONVERSATION, null,
            "Pisze do mnie okropne rzeczy")))
            .isInstanceOf(OperationNotAllowedException.class)
            .hasMessageContaining("rozmowy");
    }

    @Test
    @DisplayName("MIGAWKA PRZEZYWA skasowanie wiadomosci - to jest caly sens kopiowania tresci")
    void evidenceSurvivesMessageDeletion() {
        /* Najwazniejszy test w tej klasie. */
        makeFriends();
        messages.send("troll", "ala", text("mam cie gdzies"));
        entityManager.flush();

        ReportResponse created = reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.HARASSMENT, ReportContext.CONVERSATION, null,
            "Tak sie do mnie odzywa"));

        // Wiadomosci znikaja - tak jak przy skasowaniu konta
        messageRepository.deleteAllOfUser(troll.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.count()).isZero();
        assertThat(reports.get(created.id()).evidence())
            .extracting(EvidenceLineResponse::text)
            .containsExactly("mam cie gdzies");
    }

    /* ------------------------------------------------------------------ */
    /*  Decyzja administratora                                             */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("zamkniecie zapisuje decyzje, notatke i tego, kto ja podjal")
    void resolvingRecordsTheDecision() {
        ReportResponse created = reports.create("ala", "troll", profileReport());

        ReportResponse closed = reports.resolve("admin", created.id(),
            new ResolveReportRequest(ReportStatus.RESOLVED, "Zakaz publikowania na 48 h", null, null, null));

        assertThat(closed.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(closed.resolvedBy()).isEqualTo("admin");
        assertThat(closed.resolutionNote()).isEqualTo("Zakaz publikowania na 48 h");
        assertThat(closed.resolvedAt()).isNotNull();
    }

    /** Kasowanie posta przy zamykaniu sprawy - na prawdziwej bazie. */
    @Test
    @DisplayName("usuniecie posta przy zamykaniu sprawy NIE odbija sie od klucza obcego")
    void deletingTheReportedPostDoesNotHitForeignKey() {
        Post post = postRepository.save(new Post(troll, "obrazliwy wpis do skasowania"));
        ReportResponse created = reports.create("ala", "troll", new CreateReportRequest(
            ReportReason.HATE, ReportContext.POST, post.getId(), "Prosze o usuniecie tego wpisu"));

        decisions.resolve("admin", created.id(), new ResolveReportRequest(
            ReportStatus.RESOLVED, "Wpis usuniety", ModerationAction.DELETE_POST, null, null));

        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(post.getId()))
            .describedAs("post mial zniknac naprawde, a nie tylko w notatce")
            .isEmpty();

        /* Zgloszenie ZOSTAJE - to nie jest drobiazg. */
        assertThat(reportRepository.findById(created.id()))
            .describedAs("zgloszenie ma przetrwac skasowanie posta")
            .isPresent();
    }

    @Test
    @DisplayName("zamkniecie moze NIC nie robic z kontem - to tez jest decyzja")
    void resolvingWithoutActionLeavesTheAccountAlone() {
        ReportResponse created = reports.create("ala", "troll", profileReport());

        decisions.resolve("admin", created.id(), new ResolveReportRequest(
            ReportStatus.RESOLVED, "Upomnienie wystarczy", ModerationAction.NONE, null, null));

        entityManager.flush();
        entityManager.clear();

        User po = userRepository.findByUsername("troll").orElseThrow();
        assertThat(po.isBanned(BanKind.POSTING)).isFalse();
        assertThat(po.isBanned(BanKind.MESSAGING)).isFalse();
    }

    @Test
    @DisplayName("zamkniecie moze od razu nalozyc zakaz bezterminowy")
    void resolvingCanBanForeverOnTheRealDatabase() {
        ReportResponse created = reports.create("ala", "troll", profileReport());

        decisions.resolve("admin", created.id(), new ResolveReportRequest(
            ReportStatus.RESOLVED, "Konto zalozone po to, zeby dokuczac",
            ModerationAction.BAN_POSTING, null, true));

        entityManager.flush();
        entityManager.clear();

        User po = userRepository.findByUsername("troll").orElseThrow();
        assertThat(po.isBanned(BanKind.POSTING)).isTrue();
        assertThat(User.isForever(po.bannedUntil(BanKind.POSTING))).isTrue();
    }

    /** Zmiana decyzji po fakcie - na prawdziwej bazie. */
    @Test
    @DisplayName("zamknieta sprawe mozna otworzyc i zdecydowac inaczej")
    void closedReportCanBeReopenedAndDecidedAgain() {
        ReportResponse created = reports.create("ala", "troll", profileReport());
        decisions.resolve("admin", created.id(), new ResolveReportRequest(
            ReportStatus.DISMISSED, "Chyba bez podstaw", ModerationAction.NONE, null, null));

        ReportResponse otwarte = decisions.reopen("admin", created.id());

        assertThat(otwarte.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(otwarte.resolvedBy())
            .describedAs("po otwarciu sprawa nie ma juz decyzji ani jej autora")
            .isNull();

        // I da sie zdecydowac inaczej niz za pierwszym razem
        ReportResponse ponownie = decisions.resolve("admin", created.id(),
            new ResolveReportRequest(ReportStatus.RESOLVED, "Jednak zasadne",
                ModerationAction.NONE, null, null));

        assertThat(ponownie.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(ponownie.resolutionNote()).isEqualTo("Jednak zasadne");
    }

    @Test
    @DisplayName("otwartej sprawy nie da sie otworzyc drugi raz")
    void openReportCannotBeReopened() {
        ReportResponse created = reports.create("ala", "troll", profileReport());

        assertThatThrownBy(() -> decisions.reopen("admin", created.id()))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("drugie zamkniecie jest odrzucane - pierwsza decyzja jest wiazaca")
    void secondCloseIsRefused() {
        /*
         * Dwoch administratorow klikajacych jednoczesnie nadpisywaloby sobie nawzajem notatki, a
         * data zamkniecia przesuwalaby sie na pozniejsza.
         */
        ReportResponse created = reports.create("ala", "troll", profileReport());
        reports.resolve("admin", created.id(),
            new ResolveReportRequest(ReportStatus.DISMISSED, "Bez podstaw", null, null, null));

        assertThatThrownBy(() -> reports.resolve("admin", created.id(),
            new ResolveReportRequest(ReportStatus.RESOLVED, "Jednak jest", null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("'zamykam jako otwarte' nie jest decyzja")
    void openIsNotADecision() {
        ReportResponse created = reports.create("ala", "troll", profileReport());

        assertThatThrownBy(() -> reports.resolve("admin", created.id(),
            new ResolveReportRequest(ReportStatus.OPEN, "nie wiem", null, null, null)))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("panel pokazuje, ile WCZESNIEJSZYCH zgloszen na te osobe bylo zasadnych")
    void panelShowsHistory() {
        /*
         * Jedno zgloszenie moze byc nieporozumieniem; piate zasadne to juz wzorzec zachowania - i
         * zupelnie inna decyzja.
         */
        ReportResponse first = reports.create("ala", "troll", profileReport());
        reports.resolve("admin", first.id(),
            new ResolveReportRequest(ReportStatus.RESOLVED, "Zakaz", null, null, null));

        User celina = userRepository.save(new User("celina", "c@example.com", "hash"));
        entityManager.flush();

        ReportResponse second = reports.create(celina.getUsername(), "troll", profileReport());

        assertThat(second.priorResolved()).isEqualTo(1);
    }

    @Test
    @DisplayName("filtr pokazuje wylacznie zgloszenia czekajace na decyzje")
    void panelFiltersByStatus() {
        ReportResponse first = reports.create("ala", "troll", profileReport());
        reports.resolve("admin", first.id(),
            new ResolveReportRequest(ReportStatus.DISMISSED, "Bez podstaw", null, null, null));

        User celina = userRepository.save(new User("celina", "c@example.com", "hash"));
        entityManager.flush();
        reports.create(celina.getUsername(), "troll", profileReport());

        assertThat(reports.list(ReportStatus.OPEN, PageRequest.of(0, 20)))
            .extracting(ReportResponse::reporterUsername)
            .containsExactly("celina");

        assertThat(reports.openCount()).isEqualTo(1);
        assertThat(reports.list(null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

    private void makeFriends() {
        ala.addFriend(troll);
        userRepository.save(ala);
        userRepository.save(troll);
        entityManager.flush();
    }

    private SendMessageRequest text(String content) {
        return new SendMessageRequest(content, null, null, null);
    }

    /* ------------------------------------------------------------------ */
    /*  Zglaszajacy dowiaduje sie, jak skonczyla sie sprawa                */
    /* ------------------------------------------------------------------ */

    /** Bez tego zgloszenie znika zglaszajacemu z oczu w chwili wyslania. */
    @Test
    @DisplayName("zamkniecie sprawy powiadamia ZGLASZAJACEGO")
    void closingTheCaseNotifiesTheReporter() {
        ReportResponse created = reports.create("ala", "troll", profileReport());
        notificationRepository.deleteAll();   // czyscimy powiadomienia o samym zgloszeniu

        decisions.resolve("admin", created.id(), new ResolveReportRequest(
            ReportStatus.RESOLVED, "Konto dostalo ostrzezenie", ModerationAction.NONE, null, null));

        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findAll())
            .describedAs("zglaszajacy ma dostac dokladnie jedno powiadomienie o decyzji")
            .singleElement()
            .satisfies(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.REPORT_RESOLVED);
                assertThat(n.getRecipient().getUsername()).isEqualTo("ala");
            });
    }

    @Test
    @DisplayName("zglaszajacy widzi decyzje i notatke administratora")
    void reporterSeesTheDecisionAndTheNote() {
        ReportResponse created = reports.create("ala", "troll", profileReport());
        decisions.resolve("admin", created.id(), new ResolveReportRequest(
            ReportStatus.DISMISSED, "Opis profilu nie lamie zasad",
            ModerationAction.NONE, null, null));

        entityManager.flush();
        entityManager.clear();

        assertThat(reports.mine("ala", PageRequest.of(0, 20)).getContent())
            .singleElement()
            .satisfies(mine -> {
                assertThat(mine.status()).isEqualTo(ReportStatus.DISMISSED);
                assertThat(mine.resolutionNote()).isEqualTo("Opis profilu nie lamie zasad");
                assertThat(mine.reportedUsername()).isEqualTo("troll");
                assertThat(mine.resolvedAt()).isNotNull();
            });
    }

    /** Kazdy widzi WYLACZNIE swoje zgloszenia. */
    @Test
    @DisplayName("cudzych zgloszen nie widac na wlasnej liscie")
    void everyoneSeesOnlyTheirOwnReports() {
        reports.create("ala", "troll", profileReport());

        assertThat(reports.mine("troll", PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(reports.mine("ala", PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("otwarta sprawa nie ma jeszcze notatki")
    void openCaseHasNoNoteYet() {
        reports.create("ala", "troll", profileReport());

        assertThat(reports.mine("ala", PageRequest.of(0, 20)).getContent())
            .singleElement()
            .satisfies(mine -> {
                assertThat(mine.status()).isEqualTo(ReportStatus.OPEN);
                assertThat(mine.resolutionNote()).isNull();
                assertThat(mine.resolvedAt()).isNull();
            });
    }
}
