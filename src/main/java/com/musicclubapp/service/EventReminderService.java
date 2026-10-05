package com.musicclubapp.service;

import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.repository.EventParticipationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Przypomnienia o wydarzeniach dla zainteresowanych i idacych: w dzwonku
 * i - gdy ktos wlaczyl - na telefonie.
 *
 * <p>Progi to dni przed wydarzeniem (domyslnie 3 i 1). Kazdy prog
 * przypomina raz: zapis pamieta ostatni zaliczony prog. Sprawdzanie co
 * godzine od 9 do 21 czasu polskiego - telefon nie zadzwoni w nocy, a gdy
 * serwer stal o 9, przypomnienie przyjdzie przy nastepnym sprawdzeniu.</p>
 */
@Service
public class EventReminderService {

    private static final Logger log = LoggerFactory.getLogger(EventReminderService.class);

    private final EventParticipationRepository participations;
    private final NotificationService notifications;
    private final EventImportService importer;
    private final List<Integer> progi;

    public EventReminderService(EventParticipationRepository participations,
                                NotificationService notifications,
                                EventImportService importer,
                                @Value("${app.events.reminders.days:3,1}") String progi) {
        this.participations = participations;
        this.notifications = notifications;
        this.importer = importer;
        this.progi = Arrays.stream(progi.split(","))
            .map(String::trim).filter(p -> !p.isEmpty())
            .map(Integer::valueOf).filter(p -> p >= 0)
            .sorted().distinct().toList();
    }

    /*
     * Transakcja TUTAJ, a nie tylko na run(): harmonogram wola scheduled(), a wywolanie run() z wnetrza tej samej klasy
     * omija posrednika Springa - bez tego "przypomniane" nie trafialo do bazy i przypomnienie szlo przy kazdym przebiegu.
     */
    @Scheduled(cron = "${app.events.reminders.cron:0 7 9-21 * * *}", zone = "Europe/Warsaw")
    @Transactional
    public void scheduled() {
        int wyslane = run();
        if (wyslane > 0) {
            log.info("Przypomnienia o wydarzeniach: {}", wyslane);
        }
    }

    /** Jeden przebieg. Zwraca liczbe wyslanych przypomnien. */
    @Transactional
    public int run() {
        if (progi.isEmpty()) {
            return 0;
        }
        LocalDate dzis = importer.today();
        int wyslane = 0;
        for (EventParticipation p : participations.dueForReminder(dzis, dzis.plusDays(progi.get(progi.size() - 1)))) {
            int zostalo = (int) ChronoUnit.DAYS.between(dzis, p.getEvent().getStartDate());
            Integer prog = prog(zostalo);
            if (prog == null || (p.getRemindedDays() != null && p.getRemindedDays() <= prog)) {
                continue;
            }
            notifications.eventReminder(p.getUser(), p.getEvent(), zostalo);
            p.markReminded(prog);
            wyslane++;
        }
        return wyslane;
    }

    /**
     * Najblizszy prog, w ktorym wydarzenie juz jest: przy progach 3 i 1
     * wydarzenie za 2 dni jest w progu 3, jutrzejsze i dzisiejsze - w progu 1.
     * null, gdy do wydarzenia dalej niz najwiekszy prog.
     */
    public Integer prog(long zostaloDni) {
        for (Integer p : progi) {
            if (zostaloDni <= p) {
                return p;
            }
        }
        return null;
    }

    /** Rezygnacja - przypomnienie w dzwonku przestaje byc prawdziwe. */
    public void cancelled(Long userId, Long eventId) {
        notifications.eventRemindersGone(userId, eventId);
    }

    /** Prog w chwili zapisu - zapisujacy wie, kiedy jest wydarzenie, wiec tego progu nie przypominamy. */
    public Integer progPrzyZapisie(LocalDate data) {
        return prog(ChronoUnit.DAYS.between(importer.today(), data));
    }
}
