package com.musicclubapp.service;

import com.musicclubapp.dto.BanRequest;
import com.musicclubapp.dto.ReportResponse;
import com.musicclubapp.dto.ResolveReportRequest;
import com.musicclubapp.entity.ModerationAction;
import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Decyzja administratora w zgloszeniu - razem z jej wykonaniem. */
@Service
public class ReportDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ReportDecisionService.class);

    private final ReportRepository reportRepository;
    private final ReportService reports;
    private final UserModerationService moderation;
    private final PostService posts;

    public ReportDecisionService(ReportRepository reportRepository,
                                 ReportService reports,
                                 UserModerationService moderation,
                                 PostService posts) {
        this.reportRepository = reportRepository;
        this.reports = reports;
        this.moderation = moderation;
        this.posts = posts;
    }

    /** Zamyka zgloszenie i od razu wykonuje decyzje administratora. */
    @Transactional
    public ReportResponse resolve(String adminUsername, Long id, ResolveReportRequest request) {
        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("report", id));

        ModerationAction action = request.actionOrNone();

        /* Kasowanie posta ma sens tylko przy zgloszeniu, ktore posta dotyczy. */
        if (action == ModerationAction.DELETE_POST && report.getPost() == null) {
            throw OperationNotAllowedException.reportHasNoPost();
        }

        /* Administrator nie karze SAM SIEBIE. */
        boolean aboutSelf = report.getReported().getUsername().equals(adminUsername);
        if (aboutSelf && isPunishment(action)) {
            throw OperationNotAllowedException.ownAccount();
        }

        ReportResponse closed = reports.resolve(adminUsername, id, request);

        User target = report.getReported();
        switch (action) {
            case DELETE_POST -> {
                Long postId = report.getPost().getId();

                /* Najpierw odpinamy post od zgloszen, dopiero potem kasujemy. */
                reportRepository.detachPost(postId);

                posts.delete(postId, adminUsername);
                log.info("Administrator {} skasowal post {} przy zgloszeniu {}",
                    adminUsername, postId, id);
            }
            case BAN_POSTING, BAN_MESSAGING -> moderation.setBan(adminUsername, target.getId(),
                action.banKind(), new BanRequest(request.hours(), request.forever()));
            case DELETE_ACCOUNT -> moderation.deleteUser(adminUsername, target.getId());
            case NONE -> log.info("Administrator {} zamknal zgloszenie {} bez dzialan",
                adminUsername, id);
        }

        return closed;
    }

    /** Otwiera zamknieta sprawe z powrotem, zeby dalo sie zdecydowac inaczej. */
    @Transactional
    public ReportResponse reopen(String adminUsername, Long id) {
        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("report", id));

        if (!report.reopen()) {
            // Juz otwarte - nie ma czego zmieniac, a ciche "ok" myliloby
            throw OperationNotAllowedException.reportNotClosed();
        }

        log.info("Administrator {} otworzyl ponownie zgloszenie {}", adminUsername, id);
        return reports.get(id);
    }

    /** Czy to dzialanie jest kara wymierzona w KONTO. */
    private boolean isPunishment(ModerationAction action) {
        return action.banKind() != null || action == ModerationAction.DELETE_ACCOUNT;
    }
}
