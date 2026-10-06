package com.musicclubapp.service;

import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventSourceEntry;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.repository.EventSourceEntryRepository;
import com.musicclubapp.repository.MusicEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jeden koncert = jedno wydarzenie, choc wiemy o nim z kilku zrodel.
 *
 * <p>Wydarzenie ze zrodla pobocznego (Bandsintown, Songkick) laczymy z naszym, gdy zgodnosc jest dobra: ten sam dzien
 * i kraj, to samo miejsce (sala do 500 m albo ta sama nazwa sali w tym samym miescie) i ten sam wykonawca albo ta
 * sama nazwa; przy dwoch znanych godzinach - najwyzej 3 godziny roznicy. Inaczej to inny koncert i dostaje wlasna
 * karte. Polaczone nie nadpisuje danych glownego zrodla, tylko uzupelnia braki (godzina, wspolrzedne, adres,
 * zdjecie, bilety, opis, sklad) - i robi to po kazdym imporcie od nowa z zapamietanych wpisow
 * ({@link EventSourceEntry}).</p>
 *
 * <p>Ticketmaster jest glownym zrodlem: wydarzenie zalozone z Bandsintown przejmuje, gdy pokaze ten sam koncert, a gdy
 * sam je wycofa, a inne zrodlo dalej je pokazuje - oddaje je temu zrodlu zamiast kasowac.</p>
 */
@Service
public class EventMerger {

    /** Ta sama sala - wspolrzedne z roznych serwisow roznia sie o kilkadziesiat metrow. */
    static final double TA_SAMA_SALA_KM = 0.5;
    /** To samo miasto, gdy klucze miast sie roznia ("Warsaw" / "Warszawa" lapie klucz, ale np. dzielnice nie). */
    static final double TA_SAMA_OKOLICA_KM = 30;
    /** Wieksza roznica godzin = dwa koncerty tego samego dnia (np. popoludniowy i wieczorny). */
    static final Duration MAX_ROZNICA_GODZIN = Duration.ofHours(3);
    /** Jak dlugo po ostatnim widzeniu wpis zrodla pobocznego jeszcze "trzyma" wydarzenie. */
    static final Duration WPIS_AKTUALNY = Duration.ofDays(3);

    /** Slowa, ktore w nazwach sal nic nie znacza ("Klub Progresja" to "Progresja"). */
    private static final Set<String> OGOLNE = Set.of("klub", "club", "hala", "sala", "centrum", "center", "centre",
        "live", "music", "muzyczny", "koncertowa", "venue", "the");

    private final MusicEventRepository events;
    private final EventSourceEntryRepository sources;

    @Autowired
    public EventMerger(MusicEventRepository events, EventSourceEntryRepository sources) {
        this.events = events;
        this.sources = sources;
    }

    /** Wylaczony - dla testow, ktore skladaja import recznie i nie potrzebuja wielu zrodel. */
    public static EventMerger off() {
        return new EventMerger(null, null);
    }

    private boolean on() {
        return events != null && sources != null;
    }

    /* ------------------------------------------------------------------ */
    /*  Zgodnosc                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Jak bardzo wydarzenie pasuje do wydarzenia ze zrodla: 0 = to nie ten koncert. Wyzej = pewniej (sala po
     * wspolrzednych 3, po nazwie 3, wykonawca 3, nazwa 2, godzina 1).
     */
    static int score(MusicEvent e, ExternalEvent x) {
        if (!e.getStartDate().equals(x.date())) {
            return 0;
        }
        if (e.getCountryCode() != null && x.countryCode() != null && !e.getCountryCode().equals(x.countryCode())) {
            return 0;
        }
        boolean godzina = false;
        if (e.getStartTime() != null && x.time() != null) {
            long roznica = Math.abs(Duration.between(e.getStartTime(), x.time()).toMinutes());
            if (roznica > MAX_ROZNICA_GODZIN.toMinutes()) {
                return 0;
            }
            godzina = roznica <= 60;
        }

        Double km = e.getLatitude() != null && e.getLongitude() != null && x.latitude() != null && x.longitude() != null
            ? CityIndex.distanceKm(e.getLatitude(), e.getLongitude(), x.latitude(), x.longitude())
            : null;
        boolean salaWspolrzedne = km != null && km <= TA_SAMA_SALA_KM;
        String kluczMiasta = EventImportService.cityKey(x.city());
        boolean miasto = (e.getCityKey() != null && e.getCityKey().equals(kluczMiasta))
            || (km != null && km <= TA_SAMA_OKOLICA_KM);
        boolean salaNazwa = miasto && sameVenue(e.getVenueName(), x.venueName());
        if (!salaWspolrzedne && !salaNazwa) {
            return 0;
        }

        String nazwaE = NameKeys.of(e.getName());
        String nazwaX = NameKeys.of(x.name());
        Set<String> skladE = e.getPerformers().stream().map(p -> NameKeys.of(p.getName()))
            .filter(k -> !k.isEmpty()).collect(Collectors.toSet());
        Set<String> skladX = x.performers().stream().map(NameKeys::of).filter(k -> !k.isEmpty())
            .collect(Collectors.toSet());
        boolean wykonawca = skladX.stream().anyMatch(k -> skladE.contains(k) || NameKeys.mentions(nazwaE, k))
            || skladE.stream().anyMatch(k -> NameKeys.mentions(nazwaX, k));
        boolean nazwa = !nazwaE.isEmpty() && (nazwaE.equals(nazwaX)
            || (nazwaE.length() >= 5 && NameKeys.mentions(nazwaX, nazwaE))
            || (nazwaX.length() >= 5 && NameKeys.mentions(nazwaE, nazwaX)));
        if (!wykonawca && !nazwa) {
            return 0;
        }
        return (salaWspolrzedne ? 3 : 0) + (salaNazwa ? 3 : 0) + (wykonawca ? 3 : 0) + (nazwa ? 2 : 0) + (godzina ? 1 : 0);
    }

    /** "Klub Progresja" i "Progresja", "COS Torwar" i "Torwar" - ta sama sala. */
    static boolean sameVenue(String a, String b) {
        Set<String> x = venueWords(a);
        Set<String> y = venueWords(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        Set<String> mniejszy = x.size() <= y.size() ? x : y;
        Set<String> wiekszy = mniejszy == x ? y : x;
        return wiekszy.containsAll(mniejszy) && mniejszy.stream().anyMatch(s -> s.length() >= 4);
    }

    private static Set<String> venueWords(String name) {
        String key = NameKeys.of(name);
        if (key.isEmpty()) {
            return Set.of();
        }
        Set<String> slowa = new HashSet<>(Arrays.asList(key.split(" ")));
        slowa.removeAll(OGOLNE);
        return slowa;
    }

    /** Najlepiej pasujace; przy remisie starsze (mniejszy numer). */
    static Optional<MusicEvent> best(Collection<MusicEvent> candidates, ExternalEvent x) {
        return candidates.stream()
            .filter(e -> score(e, x) > 0)
            .max(Comparator.comparingInt((MusicEvent e) -> score(e, x))
                .thenComparing(MusicEvent::getId, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /* ------------------------------------------------------------------ */
    /*  Zrodla poboczne                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Wydarzenie ze zrodla pobocznego: znane po numerze - uaktualnia wpis; pasujace do naszego - dopisuje wpis i
     * uzupelnia braki; inaczej zaklada nowe wydarzenie, ktorego glownym zrodlem jest to zrodlo. Wolajacy trzyma
     * transakcje.
     */
    public MusicEvent absorb(ExternalEvent x, LocalDateTime now) {
        EventSourceEntry wpis = sources.findBySourceAndExternalId(x.source(), x.externalId()).orElse(null);
        MusicEvent event;
        if (wpis != null) {
            event = wpis.getEvent();
        } else {
            event = best(events.onDay(x.date(), x.countryCode()), x).orElse(null);
            if (event == null) {
                event = new MusicEvent(x.source().externalId(x.externalId()));
                event.reassign(x.source(), x.source().externalId(x.externalId()));
                applyPrimary(event, x, now);
                event = events.save(event);
            }
            wpis = new EventSourceEntry(event, x.source(), x.externalId());
        }
        wpis.update(x.url(), x.time(), x.venueName(), x.address(), x.latitude(), x.longitude(), x.ticketUrl(),
            x.imageUrl(), TicketmasterClient.clean(x.description(), EventSourceEntry.MAX_DESCRIPTION), x.performers(), now);
        sources.save(wpis);
        if (event.getSource() == x.source() && event.getExternalId().equals(x.source().externalId(x.externalId()))) {
            applyPrimary(event, x, now);
        }
        refill(List.of(event));
        return event;
    }

    /** Dane wydarzenia, ktorego glownym zrodlem jest to zrodlo poboczne - jak {@code apply} przy Ticketmasterze. */
    private static void applyPrimary(MusicEvent event, ExternalEvent x, LocalDateTime now) {
        event.describe(x.name(), TicketmasterClient.clean(x.description(), 4000), x.status(), null, null);
        event.schedule(x.date(), x.time());
        event.place(null, x.venueName(), x.city(), EventImportService.cityKey(x.city()), x.address(), x.latitude(),
            x.longitude());
        event.link(x.ticketUrl(), x.imageUrl(), x.imageUrl());
        event.groupAs(EventImportService.seriesKey(x.name(), null, x.venueName()));
        event.replacePerformers(x.performers().stream().map(p -> new EventPerformer(null, p)).toList());
        event.inCountry(x.countryCode());
        event.markSeen(now);
    }

    /* ------------------------------------------------------------------ */
    /*  Ticketmaster                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Nowe wydarzenie z Ticketmastera, ktore mamy juz z innego zrodla: Ticketmaster je przejmuje (ten sam numer,
     * zapisy, ekipy i posty zostaja), a dotychczasowe dane zostaja jako wpis zrodla pobocznego.
     */
    public Optional<MusicEvent> adopt(ExternalEvent tm) {
        if (!on()) {
            return Optional.empty();
        }
        List<MusicEvent> z = events.onDay(tm.date(), tm.countryCode()).stream()
            .filter(e -> e.getSource() != EventSource.TICKETMASTER)
            .toList();
        Optional<MusicEvent> event = best(z, tm);
        event.ifPresent(e -> e.reassign(EventSource.TICKETMASTER, tm.externalId()));
        return event;
    }

    /** Po imporcie Ticketmastera: braki znow uzupelnione z zapamietanych wpisow (jedno zapytanie na strone). */
    public void refill(Collection<MusicEvent> list) {
        if (!on() || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(MusicEvent::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<EventSourceEntry>> wpisy = sources.ofEvents(ids).stream()
            .collect(Collectors.groupingBy(s -> s.getEvent().getId()));
        for (MusicEvent e : list) {
            for (EventSourceEntry s : wpisy.getOrDefault(e.getId(), List.of())) {
                e.fillGaps(s);
            }
        }
    }

    /**
     * Ticketmaster juz nie pokazuje wydarzenia, ale inne zrodlo widzialo je niedawno - wydarzenie przechodzi na to
     * zrodlo zamiast znikac. Zwraca, czy sie udalo.
     */
    public boolean rescue(MusicEvent event, LocalDateTime now) {
        if (!on()) {
            return false;
        }
        Optional<EventSourceEntry> swiezy = sources.ofEvents(List.of(event.getId())).stream()
            .filter(s -> s.getSource() != event.getSource())
            .filter(s -> s.getSeenAt().isAfter(now.minus(WPIS_AKTUALNY)))
            .max(Comparator.comparing(EventSourceEntry::getSeenAt));
        swiezy.ifPresent(s -> {
            event.reassign(s.getSource(), s.getSource().externalId(s.getExternalId()));
            event.markSeen(s.getSeenAt());
        });
        return swiezy.isPresent();
    }
}
