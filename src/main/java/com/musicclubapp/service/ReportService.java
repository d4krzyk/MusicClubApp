package com.musicclubapp.service;

import com.musicclubapp.dto.CreateReportRequest;
import com.musicclubapp.dto.EvidenceLineResponse;
import com.musicclubapp.dto.MyReportResponse;
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

/** Zgloszenia uzytkownikow: skladanie, limity i decyzje administratora. */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** Ile zgloszen mozna zlozyc w ciagu doby. */
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
         * Powiadomienie dla administratorow - w TEJ SAMEJ transakcji, bez zadnego "na wszelki
         * wypadek" wokol.
         */
        notifications.reportFiled(userRepository.findByRole(Role.ADMIN), reporter);

        log.info("Uzytkownik {} zglosil {} ({}, {})",
            reporterUsername, reportedUsername, request.reason(), request.context());

        return toResponse(saved);
    }

    /** Dwie blokady przed nadużywaniem - w tej kolejnosci, i to ma znaczenie. */
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

    /** Dokłada do zgloszenia migawke tresci - patrz ReportEvidence. */
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

        /* Post musi nalezec do zglaszanego. */
        if (!post.getAuthor().getId().equals(reported.getId())) {
            throw OperationNotAllowedException.reportWrongAuthor();
        }

        /* ...i musi byc widoczny dla zglaszajacego. */
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

        /* Zapytanie oddaje od NAJNOWSZEJ, a dowod czyta sie jak rozmowe - od poczatku. */
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

    /** Zamyka zgloszenie decyzja administratora. */
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

        /* Zglaszajacy dowiaduje sie, ze sprawa zostala rozpatrzona. */
        notifications.reportResolved(report.getReporter(), requireUser(adminUsername));

        return toResponse(report);
    }

    /** Wlasne zgloszenia zalogowanego wraz z tym, jak sie skonczyly. */
    @Transactional(readOnly = true)
    public Page<MyReportResponse> mine(String username, Pageable pageable) {
        return reportRepository.findByReporterUsernameOrderByCreatedAtDesc(username, pageable)
            .map(report -> new MyReportResponse(
                report.getId(),
                report.getReported().getUsername(),
                report.getReason(),
                report.getContext(),
                report.getCreatedAt(),
                report.getStatus(),
                report.getResolutionNote(),
                report.getResolvedAt()));
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /** Kasuje zgloszenia zwiazane z kontem - przy jego usuwaniu. */
    /** Odpina posty jednego autora od CUDZYCH zgloszen - przed ich skasowaniem. */
    @Transactional
    public void detachPostsOf(Long authorId) {
        reportRepository.detachPostsOfAuthor(authorId);
    }

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
            /* Ile WCZESNIEJSZYCH zgloszen na te osobe uznano za zasadne. */
            reportRepository.countByReportedIdAndStatus(
                reported.getId(), ReportStatus.RESOLVED));
    }
}
