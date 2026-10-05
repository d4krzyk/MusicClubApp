package com.musicclubapp.service;

import com.musicclubapp.entity.CrewRequest;
import com.musicclubapp.repository.CrewRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Raz na dobe: kasuje prosby o miejsce w ekipie, ktore nic juz nie znacza - odrzucone po tygodniu karencji i czekajace
 * na koncert, ktory sie odbyl. Czekajaca znika tez z dzwonka zakladajacego. Reszte (ekipy, czaty, prosby) zabiera
 * wydarzenie, gdy import kasuje je miesiac po dacie.
 */
@Component
public class CrewRequestCleanup {

    private static final Logger log = LoggerFactory.getLogger(CrewRequestCleanup.class);

    private final CrewRequestRepository requests;
    private final NotificationService notifications;
    private final EventImportService importer;
    private final Clock clock;

    public CrewRequestCleanup(CrewRequestRepository requests, NotificationService notifications,
                              EventImportService importer, Clock clock) {
        this.requests = requests;
        this.notifications = notifications;
        this.importer = importer;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.crews.cleanup.initial-delay-ms:660000}",
               fixedDelayString = "${app.crews.cleanup.interval-ms:86400000}")
    @Transactional
    public void clean() {
        List<CrewRequest> stare = requests.expired(LocalDateTime.now(clock).minus(CrewService.PROSBA_PO_ODMOWIE),
            importer.today());
        for (CrewRequest r : stare) {
            if (r.isPending()) {
                notifications.crewRequestGone(r.getUser().getId(), r.getCrew().getId());
            }
        }
        requests.deleteAll(stare);
        if (!stare.isEmpty()) {
            log.info("Ekipy: usunieto {} nieaktualnych prosb", stare.size());
        }
    }
}
