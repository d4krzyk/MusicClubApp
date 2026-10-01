package com.musicclubapp.service;

import com.musicclubapp.entity.ClanJoinRequest;
import com.musicclubapp.repository.ClanJoinRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Raz na dobe: kasuje prosby o dolaczenie, na ktore nikt nie odpowiedzial przez miesiac, oraz
 * odrzucone, ktorych tydzien karencji minal. Bez tego stare prosby wisialyby na liscie zarzadu,
 * a po stronie proszacego zajmowalyby limit.
 */
@Component
public class ClanRequestCleanup {

    private static final Logger log = LoggerFactory.getLogger(ClanRequestCleanup.class);

    static final Duration OCZEKUJACA_PO = Duration.ofDays(30);

    private final ClanJoinRequestRepository requests;
    private final NotificationService notifications;
    private final Clock clock;

    public ClanRequestCleanup(ClanJoinRequestRepository requests, NotificationService notifications, Clock clock) {
        this.requests = requests;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.clans.cleanup.initial-delay-ms:600000}",
               fixedDelayString = "${app.clans.cleanup.interval-ms:86400000}")
    @Transactional
    public void clean() {
        LocalDateTime teraz = LocalDateTime.now(clock);
        List<ClanJoinRequest> stare = requests.expired(teraz.minus(OCZEKUJACA_PO),
            teraz.minus(ClanService.PROSBA_PO_ODMOWIE));
        for (ClanJoinRequest r : stare) {
            notifications.clanRequestGone(r.getUser().getId(), r.getClan().getId());
        }
        requests.deleteAll(stare);
        if (!stare.isEmpty()) {
            log.info("Klany: usunieto {} starych prosb o dolaczenie", stare.size());
        }
    }
}
