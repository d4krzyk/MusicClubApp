package com.musicclubapp.service;

import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

/** Wydarzenia do testow - zapisywane wprost, bez udawanego Ticketmastera. */
final class WydarzeniaTestowe {

    private WydarzeniaTestowe() {
    }

    static MusicEvent wydarzenie(String id, String nazwa, LocalDate data, String miasto,
                                 String gatunek, String podgatunek, String... sklad) {
        MusicEvent event = new MusicEvent(id);
        event.describe(nazwa, null, null, gatunek, podgatunek);
        event.schedule(data, LocalTime.of(20, 0));
        event.place("V-" + id, "Klub " + id, miasto, EventImportService.cityKey(miasto), null, null, null);
        event.link(null, null, null);
        event.groupAs(EventImportService.seriesKey(nazwa, "V-" + id, "Klub " + id));
        event.replacePerformers(Arrays.stream(sklad).map(s -> new EventPerformer(null, s)).toList());
        event.inCountry("PL");
        event.markSeen(LocalDateTime.of(2026, 9, 28, 0, 0));
        return event;
    }

    /**
     * Import z zegarem testowym - w jednym miejscu, zeby zmiana konstruktora
     * nie wymagala poprawiania kazdej klasy testowej osobno. Limit zapytan
     * wysoki: testy sprawdzajace limit ustawiaja go same.
     */
    static EventImportService importer(TicketmasterClient ticketmaster, MusicEventRepository events,
                                       EventParticipationRepository participations,
                                       PerformerTagService tags, UserRepository users,
                                       PlatformTransactionManager tx, Clock clock) {
        return importer(ticketmaster, events, participations, tags, users, tx, clock, 1000);
    }

    static EventImportService importer(TicketmasterClient ticketmaster, MusicEventRepository events,
                                       EventParticipationRepository participations,
                                       PerformerTagService tags, UserRepository users,
                                       PlatformTransactionManager tx, Clock clock, int limitNaKraj) {
        return new EventImportService(ticketmaster, events, participations, tags, users, tx, clock,
            0, EventImportServiceTest.SZESC_GODZIN, limitNaKraj);
    }
}
