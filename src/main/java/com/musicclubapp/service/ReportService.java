package com.musicclubapp.service;

import com.musicclubapp.dto.CreateReportRequest;
import com.musicclubapp.dto.EvidenceLineResponse;
import com.musicclubapp.dto.MyReportResponse;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.Comment;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.GifAttachment;
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
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.CommentRepository;
import com.musicclubapp.repository.ClanRepository;
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
    private final ClanRepository clanRepository;
    private final CommentRepository commentRepository;
    private final BlockService blocks;
    private final ClanMemberRepository clanMembers;
    private final ProfileCardService cards;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         MessageRepository messageRepository,
                         NotificationService notifications,
                         ClanRepository clanRepository,
                         ClanMemberRepository clanMembers,
                         CommentRepository commentRepository,
                         BlockService blocks,
                         ProfileCardService cards) {
        this.cards = cards;
        this.blocks = blocks;
        this.commentRepository = commentRepository;
        this.clanRepository = clanRepository;
        this.clanMembers = clanMembers;
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

    /**
     * Zgloszenie klanu (nazwa, skrot, opis, obrazy). "Zglaszanym" jest zalozyciel - to on odpowiada
     * za klan - a klan jest dolaczony do zgloszenia razem z migawka tego, co zglaszano: po zmianie nazwy
     * albo rozwiazaniu klanu administrator dalej widzi, o co chodzilo.
     */
    @Transactional
    public ReportResponse createForClan(String reporterUsername, Long clanId, CreateReportRequest request) {
        User reporter = requireUser(reporterUsername);
        Clan clan = clanRepository.findById(clanId)
            .orElseThrow(() -> new NoSuchElementFoundException("clan", clanId));
        User founder = clanMembers.ofClan(clanId).stream()
            .filter(m -> m.getRole() == ClanRole.FOUNDER)
            .map(ClanMember::getUser)
            .findFirst()
            .orElseThrow(() -> new NoSuchElementFoundException("clan", clanId));

        if (founder.getId().equals(reporter.getId())) {
            throw OperationNotAllowedException.reportSelf();
        }
        if (reportRepository.existsByReporterIdAndClanIdAndStatus(reporter.getId(), clanId, ReportStatus.OPEN)) {
            throw OperationNotAllowedException.reportAlreadyOpen();
        }
        if (reportRepository.countByReporterIdAndCreatedAtAfter(
                reporter.getId(), LocalDateTime.now().minusDays(1)) >= MAX_PER_DAY) {
            throw OperationNotAllowedException.reportLimit(MAX_PER_DAY);
        }

        Report report = new Report(reporter, founder, request.reason(), ReportContext.CLAN,
            request.description().trim());
        report.setClan(clan);
        report.addEvidence(new ReportEvidence("[" + clan.getTag() + "] " + clan.getName(),
            clan.getDescription() == null ? "" : clan.getDescription(), clan.getCreatedAt()));

        Report saved = reportRepository.save(report);
        notifications.reportFiled(userRepository.findByRole(Role.ADMIN), reporter);
        log.info("Uzytkownik {} zglosil klan {} (#{}) ({})", reporterUsername, clan.getName(), clanId, request.reason());
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
            case COMMENT -> attachComment(report, reporter, reported, request.commentId());
            case CONVERSATION -> attachConversation(report, reporter, reported);
            case PROFILE -> attachProfileCard(report, reported);
            // Klan zglasza sie osobna sciezka (createForClan) - tu nie wiadomo, ktorego dotyczy
            case CLAN -> throw OperationNotAllowedException.reportClanEndpoint();
        }
    }

    /**
     * Migawka karty profilu: opis, "szukam", pytania i adresy zdjec galerii - ktos mogl je zmienic albo
     * usunac, zanim administrator otworzy zgloszenie. Sam login i awatar i tak widac przy zgloszeniu.
     */
    private void attachProfileCard(Report report, User reported) {
        var karta = cards.of(reported);
        String kto = reported.getUsername();
        LocalDateTime teraz = LocalDateTime.now();
        if (karta.bio() != null && !karta.bio().isBlank()) {
            report.addEvidence(new ReportEvidence(kto, "[O mnie] " + karta.bio(), teraz));
        }
        if (!karta.lookingFor().isEmpty()) {
            report.addEvidence(new ReportEvidence(kto, "[Szukam] " + karta.lookingFor(), teraz));
        }
        karta.prompts().forEach(p -> report.addEvidence(
            new ReportEvidence(kto, "[" + p.prompt() + "] " + p.answer(), teraz)));
        karta.photos().forEach(p -> report.addEvidence(
            new ReportEvidence(kto, "[Zdjecie] " + p.url(), teraz)));
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

    private void attachComment(Report report, User reporter, User reported, Long commentId) {
        if (commentId == null) {
            throw OperationNotAllowedException.reportNeedsComment();
        }
        Comment comment = commentRepository.findWithContext(commentId)
            .orElseThrow(() -> new NoSuchElementFoundException("comment", commentId));

        /* Komentarz musi nalezec do zglaszanego... */
        if (!comment.getAuthor().getId().equals(reported.getId())) {
            throw OperationNotAllowedException.reportWrongAuthor();
        }
        /* ...i musi byc widoczny dla zglaszajacego: post (klan, "tylko znajomi") i brak blokady z autorem posta. */
        if (!comment.getPost().isVisibleTo(reporter)
            || blocks.eitherWay(reporter.getId(), comment.getPost().getAuthor().getId())) {
            throw new NoSuchElementFoundException("comment", commentId);
        }

        report.setComment(comment);
        report.addEvidence(new ReportEvidence(
            comment.getAuthor().getUsername(),
            withGif(comment.getContent(), comment.getGif()),
            comment.getCreatedAt()));
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
                withGif(message.getContent() != null ? message.getContent() : musicLabel(message), message.getGif()),
                message.getCreatedAt()));
        }
    }

    /** Dowod ma pokazac tez GIF - sam adres i opis, bo pliku nie przechowujemy. */
    private static String withGif(String text, GifAttachment gif) {
        if (gif == null) {
            return text;
        }
        return text == null || text.isBlank() ? gif.describe() : text + "\n" + gif.describe();
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

    /** Odpina posty klanu od zgloszen - przed rozwiazaniem klanu. */
    @Transactional
    public void detachPostsOfClan(Long clanId) {
        reportRepository.detachPostsOfClan(clanId);
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
            report.getPost() != null ? report.getPost().getId()
                : report.getComment() != null ? report.getComment().getPost().getId() : null,
            report.getClan() == null ? null : report.getClan().getId(),
            report.getComment() == null ? null : report.getComment().getId(),
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
