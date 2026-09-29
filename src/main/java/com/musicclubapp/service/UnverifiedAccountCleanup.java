package com.musicclubapp.service;

import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Co godzine: kasuje konta, ktore przez tydzien nie potwierdzily adresu,
 * i przeterminowane linki.
 *
 * <p>Bez tego ktos, kto zalozyl konto na cudzy adres, blokowalby go na
 * zawsze - wlasciciel adresu nie moglby sie zarejestrowac, bo "adres
 * zajety". Po tygodniu login i adres wracaja do puli.</p>
 *
 * <p>Osobna klasa, a nie metoda w EmailVerificationService: kasowanie konta
 * idzie przez AccountDeletionService, a ta posrednio zalezy od
 * potwierdzania (przez UserService) - razem bylby krag zaleznosci.</p>
 */
@Component
public class UnverifiedAccountCleanup {

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountCleanup.class);

    static final Duration USUN_PO = Duration.ofDays(7);

    private final EmailVerificationService verification;
    private final AccountDeletionService deletion;
    private final UserRepository users;
    private final MailRateLimiter limiter;
    private final TransactionTemplate tx;
    private final Clock clock;

    public UnverifiedAccountCleanup(EmailVerificationService verification,
                                    AccountDeletionService deletion,
                                    UserRepository users,
                                    MailRateLimiter limiter,
                                    PlatformTransactionManager transactionManager,
                                    Clock clock) {
        this.verification = verification;
        this.deletion = deletion;
        this.users = users;
        this.limiter = limiter;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.mail.cleanup.initial-delay-ms:120000}",
               fixedDelayString = "${app.mail.cleanup.interval-ms:3600000}")
    public void clean() {
        limiter.forgetOld();
        if (!verification.enabled()) {
            return;
        }
        verification.deleteExpired();

        LocalDateTime granica = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).minus(USUN_PO);
        List<Long> doUsuniecia = users.unverifiedCreatedBefore(granica).stream().map(User::getId).toList();
        int usuniete = 0;
        for (Long id : doUsuniecia) {
            // Kazde konto w osobnej transakcji - blad przy jednym nie wstrzymuje reszty
            try {
                tx.executeWithoutResult(status -> users.findById(id).ifPresent(deletion::erase));
                usuniete++;
            } catch (RuntimeException e) {
                log.warn("Nie udalo sie usunac niepotwierdzonego konta {}: {}", id, e.getMessage());
            }
        }
        if (usuniete > 0) {
            log.info("Usunieto {} kont bez potwierdzonego adresu (starszych niz {} dni)", usuniete, USUN_PO.toDays());
        }
    }
}
