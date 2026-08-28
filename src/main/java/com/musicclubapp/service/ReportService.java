package com.musicclubapp.service;

import com.musicclubapp.dto.CreateReportRequest;
import com.musicclubapp.dto.EvidenceLineResponse;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportEvidence;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.MessageRepository;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zgloszenia uzytkownikow: skladanie, limity i decyzje administratora.
 *
 * <p><b>Zgloszenie jest narzedziem, ktore rownie latwo obrocic przeciwko
 * komus.</b> Trzy osoby zglaszajace kogos "dla zabawy" potrafia zajac
 * administratorowi tyle samo czasu co trzy prawdziwe sprawy - i to jest
 * najprostszy sposob, zeby caly ten mechanizm przestal dzialac. Dlatego
 * limity ({@link #MAX_PER_DAY} i zakaz drugiego OTWARTEGO zgloszenia na te
 * sama osobe) nie sa dodatkiem, tylko warunkiem tego, zeby funkcja miala
 * sens.</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /**
     * Ile zgloszen mozna zlozyc w ciagu doby.
     *
     * <p>Piec to duzo jak na uczciwe uzycie (trudno w jeden dzien natknac sie
     * na piec osob lamiacych zasady) i malo jak na zasypanie panelu. Liczymy
     * ruchome 24 godziny, a nie "dzien kalendarzowy" - inaczej dalo by sie
     * zlozyc dziesiec zgloszen w godzine, po piec z kazdej strony polnocy.</p>
     */
    public static final int MAX_PER_DAY = 5;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notifications;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         MessageRepository messageRepository,
                         NotificationService notifications) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.messageRepository = messageRepository;
        this.notifications = notifications;
    }

    /* ------------------------------------------------------------------ */
    /*  Skladanie zgloszenia                                               */
    /* ------------------------------------------------------------------ */

    @Transactional
    public ReportResponse create(String reporterUsername, String reportedUsername,
                                 CreateReportRequest request) {

        User reporter = requireUser(reporterUsername);
        User reported = requireUser(reportedUsername);

        if (reporter.getId().equals(reported.getId())) {
            throw OperationNotAllowedException.reportSelf();
        }

        checkLimits(reporter, reported);

        Report report = new Report(
            reporter, reported, request.reason(), request.context(),
            request.description().trim());

        attachEvidence(report, reporter, reported, request);

        Report saved = reportRepository.save(report);

        /*
         * Powiadomienie dla administratorow. Blad tutaj NIE moze przerwac
         * zgloszenia: zgloszenie jest juz w bazie i widac je w panelu,
         * a powiadomienie to tylko szybsza droga do niego.
         */
        try {
            notifications.reportFiled(userRepository.findByRole(Role.ADMIN), reporter);
        } catch (Exception e) {
            log.warn("Nie udalo sie powiadomic administratorow o zgloszeniu {}: {}",
                saved.getId(), e.getMessage());
        }

        log.info("Uzytkownik {} zglosil {} ({}, {})",
            reporterUsername, reportedUsername, request.reason(), request.context());

        return toResponse(saved);
    }

    /**
     * Dwie blokady przed nadużywaniem - w tej kolejnosci, i to ma znaczenie.
     *
     * <p>Najpierw sprawdzamy zgloszenie na TE SAMA osobe, bo ten komunikat
     * jest konkretniejszy: mowi "to juz zglosiles", a nie ogolne "za duzo
     * zgloszen". Przy odwrotnej kolejnosci ktos, kto omylkowo klika drugi raz
     * to samo, dostawalby informacje o wyczerpanym limicie dziennym -
     * mylaca i niepotrzebnie niepokojaca.</p>
     */
    private void checkLimits(User reporter, User reported) {
        boolean alreadyOpen = reportRepository.existsByReporterIdAndReportedIdAndStatus(
            reporter.getId(), reported.getId(), ReportStatus.OPEN);

        if (alreadyOpen) {
            throw OperationNotAllowedException.reportAlreadyOpen();
        }

        long today = reportRepository.countByReporterIdAndCreatedAtAfter(
            reporter.getId(), LocalDateTime.now().minusDays(1));

        if (today >= MAX_PER_DAY) {
            throw OperationNotAllowedException.reportLimit(MAX_PER_DAY);
        }
    }

    /**
     * Dokłada do zgloszenia migawke tresci - patrz {@link ReportEvidence}.
     *
     * <p>Kazdy rodzaj zgloszenia dostaje inny dowod, bo w kazdym co innego
     * jest dowodem:</p>
     * <ul>
     *   <li><b>post</b> - jego tresc; administrator moze go tez otworzyc,
     *       ale post bywa skasowany zanim ktos zajrzy do zgloszenia,</li>
     *   <li><b>rozmowa</b> - ostatnie wiadomosci; administrator NIE MA innej
     *       drogi, zeby je zobaczyc, i mieć nie powinien,</li>
     *   <li><b>profil</b> - nic; dowodem jest sam profil, ktory kazdy
     *       zalogowany moze obejrzec.</li>
     * </ul>
     */
    private void attachEvidence(Report report, User reporter, User reported,
                                CreateReportRequest request) {
        switch (request.context()) {
            case POST -> attachPost(report, reporter, reported, request.postId());
            case CONVERSATION -> attachConversation(report, reporter, reported);
            case PROFILE -> { /* dowodem jest sam profil */ }
        }
    }

    private void attachPost(Report report, User reporter, User reported, Long postId) {
        if (postId == null) {
            throw OperationNotAllowedException.reportNeedsPost();
        }

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NoSuchElementFoundException("post", postId));

        /*
         * Post musi nalezec do zglaszanego. Bez tego sprawdzenia dalo by sie
         * podpiac pod zgloszenie cudzy post - na przyklad wlasny - i wywolac
         * dzialanie administratora wobec osoby, ktora go nie napisala.
         */
        if (!post.getAuthor().getId().equals(reported.getId())) {
            throw OperationNotAllowedException.reportWrongAuthor();
        }

        /*
         * ...i musi byc widoczny dla zglaszajacego. Post "tylko dla znajomych"
         * kogos obcego jest dla nas niewidoczny - zgloszenie go oznaczaloby
         * przyznanie sie do tego, ze jednak sie go widzialo.
         */
        if (!post.isVisibleTo(reporter)) {
            throw OperationNotAllowedException.friendsOnlyPost();
        }

        report.setPost(post);
        report.addEvidence(new ReportEvidence(
            post.getAuthor().getUsername(),
            post.getContent(),
            post.getCreatedAt()));
    }

    private void attachConversation(Report report, User reporter, User reported) {
        Page<Message> recent = messageRepository.conversation(
            reporter.getId(), reported.getId(),
            PageRequest.of(0, Report.EVIDENCE_LIMIT));

        if (recent.isEmpty()) {
            throw OperationNotAllowedException.reportEmptyConversation();
        }

        /*
         * Zapytanie oddaje od NAJNOWSZEJ, a dowod czyta sie jak rozmowe -
         * od poczatku. Odwracamy tutaj, zeby administrator nie musial
         * czytac od dolu.
         *
         * Recznie, a nie przez List.reversed(): tamta metoda pochodzi
         * z Javy 21, a projekt jest budowany na poziomie 17. Kompilowalby sie
         * na tej maszynie i przestal u kogos z nowszym JDK ustawionym inaczej.
         */
        List<Message> ordered = new ArrayList<>(recent.getContent());
        Collections.reverse(ordered);

        for (Message message : ordered) {
            report.addEvidence(new ReportEvidence(
                message.getSender().getUsername(),
                // Wiadomosc bywa samym nagraniem - wtedy dowodem jest jego tytul
                message.getContent() != null ? message.getContent()
                    : musicLabel(message),
                message.getCreatedAt()));
        }
    }

    private String musicLabel(Message message) {
        if (!message.hasMusic()) {
            return "";
        }
        return "[" + message.getMusicProvider() + "] "
            + (message.getMusicTitle() != null
                ? message.getMusicTitle()
                : message.getMusicExternalId());
    }

    /* ------------------------------------------------------------------ */
    /*  Panel administratora                                               */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public Page<ReportResponse> list(ReportStatus status, Pageable pageable) {
        Page<Report> page = status == null
            ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
            : reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(Long id) {
        return toResponse(requireReport(id));
    }

    /** Ile czeka na decyzje - liczba przy ikonie panelu. */
    @Transactional(readOnly = true)
    public long openCount() {
        return reportRepository.countByStatus(ReportStatus.OPEN);
    }

    /**
     * Zamyka zgloszenie decyzja administratora.
     *
     * <p><b>Samo zamkniecie NICZEGO nie robi z kontem</b> - nie naklada
     * zakazu, nie kasuje posta. To jest celowe: dzialania sa osobnymi
     * operacjami w panelu i administrator wykonuje te, ktore uzna za
     * potrzebne. Gdyby zamkniecie zgloszenia automatycznie karalo, kazda
     * decyzja "zasadne, ale wystarczy upomnienie" bylaby niemozliwa
     * do wyrazenia.</p>
     */
    @Transactional
    public ReportResponse resolve(String adminUsername, Long id, ResolveReportRequest request) {
        if (request.decision() == ReportStatus.OPEN) {
            throw OperationNotAllowedException.reportDecisionRequired();
        }

        Report report = requireReport(id);

        if (!report.close(request.decision(), adminUsername, request.note().trim())) {
            // Ktos byl szybszy - pierwsza decyzja jest ta wiazaca
            throw OperationNotAllowedException.reportAlreadyClosed();
        }

        log.info("Administrator {} zamknal zgloszenie {} jako {}",
            adminUsername, id, request.decision());

        return toResponse(report);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Kasuje zgloszenia zwiazane z kontem - przy jego usuwaniu.
     *
     * <p>Odpina tez posty tej osoby od CUDZYCH zgloszen: zgloszenie wskazuje
     * na post kluczem obcym, a posty ida do kasacji razem z kontem.</p>
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        reportRepository.detachPostsOfAuthor(userId);
        reportRepository.deleteAllOfUser(userId);
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }

    private Report requireReport(Long id) {
        return reportRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("report", id));
    }

    private ReportResponse toResponse(Report report) {
        User reported = report.getReported();

        return new ReportResponse(
            report.getId(),
            report.getReporter().getUsername(),
            reported.getUsername(),
            reported.getAvatarFileName() == null
                ? null
                : PostMapper.UPLOADS_PATH + reported.getAvatarFileName(),
            report.getReason(),
            report.getContext(),
            report.getPost() == null ? null : report.getPost().getId(),
            report.getDescription(),
            report.getEvidence().stream()
                .map(line -> new EvidenceLineResponse(
                    line.getAuthor(), line.getText(), line.getSentAt()))
                .toList(),
            report.getStatus(),
            report.getCreatedAt(),
            report.getResolvedAt(),
            report.getResolvedBy(),
            report.getResolutionNote(),
            /*
             * Ile WCZESNIEJSZYCH zgloszen na te osobe uznano za zasadne.
             * Liczymy przy kazdym odczycie, a nie trzymamy w kolumnie -
             * kolumna musialaby byc uaktualniana przy kazdej decyzji i przy
             * kazdym kasowaniu, czyli w dwoch miejscach naraz.
             */
            reportRepository.countByReportedIdAndStatus(
                reported.getId(), ReportStatus.RESOLVED));
    }
}
