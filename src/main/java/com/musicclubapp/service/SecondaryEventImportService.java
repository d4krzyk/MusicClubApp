package com.musicclubapp.service;

import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.EventSourceEntryRepository;
import com.musicclubapp.repository.MusicEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zrodla poboczne: Bandsintown (koncerty wykonawcow, ktorych ludzie u nas lubia) i Songkick (koncerty w okolicy
 * najwiekszych miast). Raz na dobe, kazde tylko z kluczem. Wszystko idzie przez {@link EventMerger}: ten sam koncert
 * co z Ticketmastera uzupelnia jego braki, nowy dostaje wlasna karte.
 *
 * <p>Bezpieczniki jak przy Ticketmasterze: przy zbyt wielu bledach albo zerze wynikow niczego nie usuwamy; wydarzenie
 * zalozone z tego zrodla znika (albo jest wycofywane, gdy ktos sie zapisal) dopiero po 3 dniach niewidzenia.</p>
 */
@Service
public class SecondaryEventImportService {

    private static final Logger log = LoggerFactory.getLogger(SecondaryEventImportService.class);

    /** Po ilu bledach z rzedu przerywamy przebieg (zrodlo lezy - nie ma sensu pytac dalej). */
    static final int MAX_BLEDOW = 5;
    /** Tyle naprzod - jak Ticketmaster. */
    static final int MIESIECY_NAPRZOD = 12;
    /** Songkick: najwyzej tyle stron na miasto. */
    static final int STRON_NA_MIASTO = 4;

    private final BandsintownClient bandsintown;
    private final SongkickClient songkick;
    private final EventMerger merger;
    private final EventImportService importer;
    private final MusicEventRepository events;
    private final EventSourceEntryRepository sources;
    private final EventParticipationRepository participations;
    private final ArtistRepository artists;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int maxArtists;
    private final long przerwaMs;
    private final Duration coIle;
    private final List<double[]> miasta;
    private final Map<EventSource, LocalDateTime> ostatnio = new EnumMap<>(EventSource.class);

    public SecondaryEventImportService(BandsintownClient bandsintown, SongkickClient songkick, EventMerger merger,
                                       EventImportService importer, MusicEventRepository events,
                                       EventSourceEntryRepository sources, EventParticipationRepository participations,
                                       ArtistRepository artists, PlatformTransactionManager transactionManager, Clock clock,
                                       @Value("${app.events.bandsintown.max-artists:150}") int maxArtists,
                                       @Value("${app.events.secondary.pause-ms:300}") long przerwaMs,
                                       @Value("${app.events.secondary.interval-ms:86400000}") long coIleMs,
                                       @Value("${app.events.songkick.cities:52.2297,21.0122;50.0647,19.9450;51.1079,17.0385;"
                                           + "52.4064,16.9252;54.3520,18.6466;51.7592,19.4560;50.2649,19.0238;"
                                           + "51.2465,22.5684;53.4285,14.5528;53.1325,23.1688}") String miasta) {
        this.bandsintown = bandsintown;
        this.songkick = songkick;
        this.merger = merger;
        this.importer = importer;
        this.events = events;
        this.sources = sources;
        this.participations = participations;
        this.artists = artists;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxArtists = maxArtists;
        this.przerwaMs = przerwaMs;
        this.coIle = Duration.ofMillis(coIleMs);
        this.miasta = punkty(miasta);
    }

    /** "lat,lon;lat,lon" - zle wpisy pomijane. */
    static List<double[]> punkty(String tekst) {
        List<double[]> wynik = new ArrayList<>();
        for (String para : tekst == null ? new String[0] : tekst.split(";")) {
            String[] czesci = para.split(",");
            try {
                if (czesci.length == 2) {
                    wynik.add(new double[] {Double.parseDouble(czesci[0].strip()), Double.parseDouble(czesci[1].strip())});
                }
            } catch (NumberFormatException e) {
                log.warn("Songkick: pomijam zly punkt '{}'", para);
            }
        }
        return wynik;
    }

    @Scheduled(initialDelayString = "${app.events.secondary.initial-delay-ms:600000}",
               fixedDelayString = "${app.events.secondary.check-ms:3600000}")
    public void scheduled() {
        if (bandsintown.available() && stale(EventSource.BANDSINTOWN)) {
            runBandsintown();
        }
        if (songkick.available() && stale(EventSource.SONGKICK)) {
            runSongkick();
        }
    }

    private boolean stale(EventSource zrodlo) {
        LocalDateTime ostatni = ostatnio.get(zrodlo);
        return ostatni == null || !ostatni.plus(coIle).isAfter(LocalDateTime.now(clock));
    }

    /** Wynik przebiegu jednego zrodla. */
    public record Result(int seen, int removed, String error) {
    }

    /** Koncerty najczesciej lubianych wykonawcow i tych, ktorych wydarzenia juz mamy z Bandsintown. */
    public synchronized Result runBandsintown() {
        LocalDate dzis = importer.today();
        Set<String> kogo = new LinkedHashSet<>(artists.mostLiked(PageRequest.of(0, maxArtists)));
        kogo.addAll(events.performersFrom(EventSource.BANDSINTOWN, dzis));
        Set<String> kraje = new HashSet<>(importer.activeCountries());
        LocalDateTime start = LocalDateTime.now(clock);
        int widziane = 0;
        int bledy = 0;
        for (String wykonawca : kogo) {
            List<ExternalEvent> lista;
            try {
                lista = bandsintown.artistEvents(wykonawca);
                bledy = 0;
            } catch (IllegalStateException e) {
                log.warn("Bandsintown '{}': {}", wykonawca, e.getMessage());
                if (++bledy >= MAX_BLEDOW) {
                    return zakoncz(EventSource.BANDSINTOWN, widziane, 0, "przerwane po " + MAX_BLEDOW + " bledach");
                }
                continue;
            }
            widziane += zapisz(lista, kraje, dzis, start);
            pauza();
        }
        int usuniete = widziane == 0 ? 0 : removeStale(EventSource.BANDSINTOWN, dzis, start);
        return zakoncz(EventSource.BANDSINTOWN, widziane, usuniete, null);
    }

    /** Koncerty w okolicy najwiekszych miast (Songkick szuka po miejscu). */
    public synchronized Result runSongkick() {
        LocalDate dzis = importer.today();
        LocalDate koniec = dzis.plusMonths(MIESIECY_NAPRZOD);
        Set<String> kraje = new HashSet<>(importer.activeCountries());
        LocalDateTime start = LocalDateTime.now(clock);
        int widziane = 0;
        for (double[] punkt : miasta) {
            for (int strona = 1; strona <= STRON_NA_MIASTO; strona++) {
                SongkickClient.Page wyniki;
                try {
                    wyniki = songkick.eventsNear(punkt[0], punkt[1], dzis, koniec, strona);
                } catch (IllegalStateException e) {
                    log.warn("Songkick: {}", e.getMessage());
                    return zakoncz(EventSource.SONGKICK, widziane, 0, e.getMessage());
                }
                widziane += zapisz(wyniki.events(), kraje, dzis, start);
                pauza();
                if (wyniki.last()) {
                    break;
                }
            }
        }
        int usuniete = widziane == 0 ? 0 : removeStale(EventSource.SONGKICK, dzis, start);
        return zakoncz(EventSource.SONGKICK, widziane, usuniete, null);
    }

    private Result zakoncz(EventSource zrodlo, int widziane, int usuniete, String blad) {
        if (blad == null) {
            ostatnio.put(zrodlo, LocalDateTime.now(clock));
            log.info("Import {}: {} wydarzen, usunietych {}", zrodlo.label(), widziane, usuniete);
        }
        return new Result(widziane, usuniete, blad);
    }

    /** Tylko z krajow, ktore pobieramy, i w zakresie dat Ticketmastera. Jedna transakcja na liste. */
    private int zapisz(List<ExternalEvent> lista, Set<String> kraje, LocalDate dzis, LocalDateTime teraz) {
        LocalDate koniec = dzis.plusMonths(MIESIECY_NAPRZOD);
        List<ExternalEvent> nasze = lista.stream()
            .filter(x -> x.countryCode() != null && kraje.contains(x.countryCode()))
            .filter(x -> !x.date().isBefore(dzis) && x.date().isBefore(koniec))
            .filter(x -> x.name() != null && !x.name().isBlank())
            .toList();
        if (nasze.isEmpty()) {
            return 0;
        }
        transactions.executeWithoutResult(s -> nasze.forEach(x -> merger.absorb(x, teraz)));
        return nasze.size();
    }

    /**
     * Wydarzenia zalozone z tego zrodla, ktorych nie widzialo od 3 dni: przechodza na inne zrodlo, jesli je
     * pokazuje, inaczej znikaja - albo sa wycofywane, gdy ktos sie zapisal albo napisal pod nimi post.
     */
    private int removeStale(EventSource zrodlo, LocalDate dzis, LocalDateTime teraz) {
        Integer wynik = transactions.execute(s -> {
            List<MusicEvent> stare = events.staleFrom(zrodlo, dzis, teraz.minus(EventMerger.WPIS_AKTUALNY));
            if (stare.isEmpty()) {
                return 0;
            }
            List<Long> ids = stare.stream().map(MusicEvent::getId).toList();
            Set<Long> zostaja = new HashSet<>(participations.eventsWithParticipants(ids));
            zostaja.addAll(events.withPosts(ids));
            List<MusicEvent> doUsuniecia = new ArrayList<>();
            for (MusicEvent e : stare) {
                if (merger.rescue(e, teraz)) {
                    continue;
                }
                if (zostaja.contains(e.getId())) {
                    e.withdraw(teraz);
                } else {
                    doUsuniecia.add(e);
                }
            }
            events.deleteAll(doUsuniecia);
            // Wpisy, ktorych zrodlo dawno nie pokazuje, nic juz nie uzupelniaja
            sources.deleteStale(zrodlo, teraz.minus(Duration.ofDays(14)));
            return doUsuniecia.size();
        });
        return wynik == null ? 0 : wynik;
    }

    private void pauza() {
        if (przerwaMs <= 0) {
            return;
        }
        try {
            Thread.sleep(przerwaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
