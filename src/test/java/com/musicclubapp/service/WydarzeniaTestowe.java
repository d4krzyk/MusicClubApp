package com.musicclubapp.service;

import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;

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
        event.markSeen(LocalDateTime.of(2026, 9, 28, 0, 0));
        return event;
    }
}
