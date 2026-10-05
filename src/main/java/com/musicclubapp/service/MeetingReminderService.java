package com.musicclubapp.service;

import com.musicclubapp.entity.Meeting;
import com.musicclubapp.entity.MeetingAttendee;
import com.musicclubapp.repository.MeetingAttendeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Przypomnienia o spotkaniach dla osob, ktore potwierdzily: co minute, o kazdej porze (godzine wybral zakladajacy).
 * Kazda osoba dostaje jedno przypomnienie na spotkanie; serwer, ktory stal, nadrabia je przy starcie, ale nie
 * przypomina o spotkaniu odwolanym albo zakonczonym. Kto przestal byc znajomym albo odszedl z klanu, nie dostaje.
 */
@Service
public class MeetingReminderService {

    private static final Logger log = LoggerFactory.getLogger(MeetingReminderService.class);

    private final MeetingAttendeeRepository attendees;
    private final MeetingService meetings;
    private final NotificationService notifications;
    private final Clock clock;

    public MeetingReminderService(MeetingAttendeeRepository attendees, MeetingService meetings,
                                  NotificationService notifications, Clock clock) {
        this.attendees = attendees;
        this.meetings = meetings;
        this.notifications = notifications;
        this.clock = clock;
    }

    /*
     * Transakcja TUTAJ, a nie tylko na run(): harmonogram wola scheduled(), a wywolanie run() z wnetrza tej samej klasy
     * omija posrednika Springa - bez tego "przypomniane" nie trafialo do bazy i przypomnienie szlo przy kazdym przebiegu.
     */
    @Scheduled(cron = "${app.meetings.reminders.cron:30 * * * * *}")
    @Transactional
    public void scheduled() {
        int sent = run();
        if (sent > 0) {
            log.info("Przypomnienia o spotkaniach: {}", sent);
        }
    }

    /** Jeden przebieg. Zwraca liczbe wyslanych przypomnien. */
    @Transactional
    public int run() {
        Instant now = Instant.now(clock);
        int sent = 0;
        for (MeetingAttendee a : attendees.dueForReminder(now)) {
            a.markReminded(now);
            Meeting meeting = a.getMeeting();
            if (!meetings.stillIn(meeting, a.getUser())) {
                continue;
            }
            notifications.meetingReminder(a.getUser(), meeting);
            sent++;
        }
        return sent;
    }
}
