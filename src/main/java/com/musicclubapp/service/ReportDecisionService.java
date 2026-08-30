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

/**
 * Decyzja administratora w zgloszeniu - <b>razem z jej wykonaniem</b>.
 *
 * <p><b>Dlaczego to jest osobna klasa.</b> Mieszkalo wczesniej
 * w {@code UserModerationService}, ktory urosl przez to do trzynastu
 * wstrzykiwanych zaleznosci i piecu niepowiazanych ze soba zadan: kasowanie
 * kont, kary, decyzje w zgloszeniach, ponowne otwieranie spraw i powiazania
 * sieciowe. Konstruktor z trzynastoma parametrami to nie kwestia stylu -
 * to informacja, ze klasa robi za duzo.</p>
 *
 * <p>Co gorsza, zgloszenia trafily tam z <b>powodu technicznego</b>, a nie
 * dlatego, ze tam pasuja: {@code ReportService} nie mogl siegnac po kary,
 * bo moderacja juz od niego zalezy (kasujac konto, kasuje tez jego
 * zgloszenia), a zaleznosc w druga strone zamknelaby kolo. Ta klasa rozcina
 * je uczciwie: <b>zalezy od obu i zadna nie zalezy od niej</b>, wiec granica
 * wynika teraz z rol, a nie z obejscia.</p>
 *
 * <p><b>Podzial obowiazkow.</b> {@code ReportService} wie, czym jest
 * zgloszenie (limity, dowody, zamykanie). {@code UserModerationService} wie,
 * jak ukarac konto. Ta klasa wie tylko <b>jedno</b>: ze decyzja i kara maja
 * sie wydarzyc razem albo wcale.</p>
 */
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

    /**
     * Zamyka zgloszenie i <b>od razu</b> wykonuje decyzje administratora.
     *
     * <p><b>Jedna transakcja na decyzje i kare.</b> Gdyby to byly dwa osobne
     * wywolania z przegladarki, awaria miedzy nimi zostawialaby stan, ktorego
     * nie da sie sensownie opisac: zgloszenie zamkniete z notatka "konto
     * usuniete", a konto na miejscu. Tutaj albo dzieje sie jedno i drugie,
     * albo nic.</p>
     *
     * <p><b>Kolejnosc: najpierw zamkniecie, potem kara.</b> Zamkniecie
     * sprawdza, czy ktos inny nie zdazyl juz podjac decyzji - i jesli zdazyl,
     * przerywa. Przy odwrotnej kolejnosci kara zdazylaby sie wykonac
     * <i>drugi raz</i>, zanim wyszloby na jaw, ze sprawa jest juz zamknieta.</p>
     */
    @Transactional
    public ReportResponse resolve(String adminUsername, Long id, ResolveReportRequest request) {
        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("report", id));

        ModerationAction action = request.actionOrNone();

        /*
         * Kasowanie posta ma sens tylko przy zgloszeniu, ktore posta dotyczy.
         * Sprawdzamy to PRZED zamknieciem sprawy: inaczej zgloszenie bylo by
         * juz zamkniete, gdy okaze sie, ze zadanej kary nie da sie wykonac,
         * a zamkniecia nie da sie cofnac.
         */
        if (action == ModerationAction.DELETE_POST && report.getPost() == null) {
            throw OperationNotAllowedException.reportHasNoPost();
        }

        /*
         * Administrator nie karze SAM SIEBIE.
         *
         * Zgloszenie moze dotyczyc administratora - i wtedy nadal wolno mu je
         * zamknac, bo ktos musi. Nie wolno mu natomiast przy tej okazji
         * zablokowac ani skasowac wlasnego konta: "sam sobie sedzia" to
         * z jednej strony ocena we wlasnej sprawie, a z drugiej - jedno
         * klikniecie od odebrania sobie (i moze wszystkim) dostepu do panelu.
         * Zgloszenie na INNEGO administratora jest zwyklym zgloszeniem
         * i dziala bez ograniczen.
         */
        boolean aboutSelf = report.getReported().getUsername().equals(adminUsername);
        if (aboutSelf && isPunishment(action)) {
            throw OperationNotAllowedException.ownAccount();
        }

        ReportResponse closed = reports.resolve(adminUsername, id, request);

        User target = report.getReported();
        switch (action) {
            case DELETE_POST -> {
                Long postId = report.getPost().getId();

                /*
                 * Najpierw odpinamy post od zgloszen, dopiero potem kasujemy.
                 * Odwrotna kolejnosc konczy sie odmowa bazy (klucz obcy
                 * z tabeli zgloszen) - i konczyla sie, zanim to powstalo.
                 * Odpiac trzeba WSZYSTKIE zgloszenia, nie tylko to rozpatrywane:
                 * ten sam post mogl zglosic ktos jeszcze.
                 */
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

    /**
     * Otwiera zamknieta sprawe z powrotem, zeby dalo sie zdecydowac inaczej.
     *
     * <p><b>Nie cofa wykonanych dzialan</b> - skasowanego posta nie ma,
     * a zakaz zdejmuje sie osobno w panelu kont. Cofa sie decyzja, nie jej
     * skutki; szczegoly przy {@code Report.reopen}.</p>
     */
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

    /**
     * Czy to dzialanie jest kara wymierzona w KONTO.
     *
     * <p>Usuniecie posta swiadomie nie jest tu wymienione: post to pojedyncza
     * tresc i skasowanie wlasnego wpisu nie odbiera nikomu dostepu do niczego.
     * Zakazy i usuniecie konta - owszem.</p>
     */
    private boolean isPunishment(ModerationAction action) {
        return action.banKind() != null || action == ModerationAction.DELETE_ACCOUNT;
    }
}
