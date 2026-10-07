package com.musicclubapp.service;

import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cykliczne pobieranie koncertow z Ticketmastera do naszej bazy.
 *
 * Co kilka godzin przechodzi przez najblizszy rok miesiac po miesiacu,
 * dopisuje nowe wydarzenia, uaktualnia znane i - tylko po imporcie, ktory
 * przeszedl w calosci - usuwa te, ktorych Ticketmaster juz nie ma.
 */
@Service
public class EventImportService {

    private static final Logger log = LoggerFactory.getLogger(EventImportService.class);

    /**
     * "Dzis" liczymy po polskiemu - aplikacja jest polska, a przy innych
     * krajach Europy roznica to godzina, dwie. Okresy importu sa w UTC,
     * wiec nie gubia wydarzen niezaleznie od strefy.
     */
    public static final ZoneId STREFA = ZoneId.of("Europe/Warsaw");

    /** Jak daleko w przod patrzymy. Dalej i tak malo co jest juz w sprzedazy. */
    private static final int MIESIECY_NAPRZOD = 12;

    /** Po ilu dniach od koncertu usuwamy go z bazy. */
    private static final int DNI_PO_WYDARZENIU = 30;

    /**
     * Ta sama nazwa miasta po polsku i po angielsku. Ticketmaster podaje
     * zwykle angielska, ale nie zawsze - a filtr ma pokazac jedno miasto.
     * Krakow/Kraków czy Lodz/Łódź nie wymagaja wpisu: po zdjeciu polskich
     * znakow sa juz takie same.
     */
    private static final Map<String, String> TE_SAME_MIASTA = Map.of(
        "warszawa", "warsaw");

    private final TicketmasterClient ticketmaster;
    private final MusicEventRepository repository;
    private final EventParticipationRepository participations;
    private final PerformerTagService performerTags;
    private final UserRepository users;
    private final EventMerger merger;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Ile zapytan na jeden kraj w jednym przebiegu. Polska to kilkanascie,
     * ale USA maja dziesiatki tysiecy koncertow rocznie - bez limitu jeden
     * kraj zjadlby caly dzienny limit Ticketmastera (5000). Gdy limit sie
     * skonczy, kraj jest pokryty od dzis do miejsca, w ktorym stanelismy.
     */
    private final int limitNaKraj;

    /** Kiedy ostatnio udal sie import danego kraju - po tym poznajemy, co odswiezyc. */
    private final Map<String, LocalDateTime> ostatnioUdany = new ConcurrentHashMap<>();

    /** Kraje, ktorych import wlasnie idzie w tle, bo ktos je przed chwila wybral. */
    private final Set<String> wKolejce = ConcurrentHashMap.newKeySet();

    private final ExecutorService tlo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "import-wydarzen-kraju");
        t.setDaemon(true);
        return t;
    });

    /** Przerwa miedzy zapytaniami - Ticketmaster pozwala na 5 na sekunde. */
    private final long przerwaMs;

    /** Co ile powtarzamy UDANY import. Po nieudanym probujemy przy najblizszym sprawdzeniu. */
    private final Duration coIle;

    /** Wynik ostatniego przebiegu. W pamieci - po restarcie i tak zaraz bedzie nowy. */
    private volatile ImportStatus lastRun;

    @Autowired
    public EventImportService(TicketmasterClient ticketmaster,
                              MusicEventRepository repository,
                              EventParticipationRepository participations,
                              PerformerTagService performerTags,
                              UserRepository users,
                              EventMerger merger,
                              PlatformTransactionManager transactionManager,
                              Clock clock,
                              @Value("${app.events.import.pause-ms:250}") long przerwaMs,
                              @Value("${app.events.import.interval-ms:21600000}") long coIleMs,
                              @Value("${app.events.import.max-requests-per-country:60}") int limitNaKraj) {
        this.ticketmaster = ticketmaster;
        this.merger = merger;
        this.repository = repository;
        this.participations = participations;
        this.performerTags = performerTags;
        this.users = users;
        this.limitNaKraj = limitNaKraj;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.przerwaMs = przerwaMs;
        this.coIle = Duration.ofMillis(coIleMs);
    }

    /** Bez innych zrodel - dla testow, ktore skladaja import recznie. */
    public EventImportService(TicketmasterClient ticketmaster, MusicEventRepository repository,
                              EventParticipationRepository participations, PerformerTagService performerTags,
                              UserRepository users, PlatformTransactionManager transactionManager, Clock clock,
                              long przerwaMs, long coIleMs, int limitNaKraj) {
        this(ticketmaster, repository, participations, performerTags, users, EventMerger.off(), transactionManager,
            clock, przerwaMs, coIleMs, limitNaKraj);
    }

    /** Jak poszedl ostatni import. */
    public record ImportStatus(LocalDateTime finishedAt, boolean success, int events,
                               int removed, String error) { }

    public boolean available() {
        return ticketmaster.available();
    }

    public ImportStatus lastRun() {
        return lastRun;
    }

    /**
     * Uruchamia sie sam: pierwszy raz chwile po starcie serwera, potem
     * sprawdza co kwadrans, czy ktorys kraj trzeba odswiezyc.
     *
     * Kraj po UDANYM imporcie czeka 6 godzin. Po NIEUDANYM - idzie przy
     * najblizszym sprawdzeniu. Pierwsza wersja miala po prostu "co
     * 6 godzin", przez co jedno zerwane polaczenie zostawialo pusta zakladke
     * na cale 6 godzin, nawet gdy siec wrocila po minucie.
     *
     * fixedDelay, a nie fixedRate - kolejne sprawdzenie liczy sie od KONCA
     * poprzedniego, wiec dwa importy nigdy nie nachodza na siebie.
     */
    @Scheduled(initialDelayString = "${app.events.import.initial-delay-ms:30000}",
               fixedDelayString = "${app.events.import.check-ms:900000}")
    public void scheduledImport() {
        if (!ticketmaster.available()) {
            return;
        }
        List<String> doOdswiezenia = activeCountries().stream().filter(this::stale).toList();
        if (!doOdswiezenia.isEmpty()) {
            importCountries(doOdswiezenia);
        }
    }

    /** Jeden pelny przebieg: wszystkie kraje, ktore ktos wybral, plus Polska. */
    public ImportStatus runImport() {
        return importCountries(activeCountries());
    }

    /**
     * Ktos wlasnie wybral kraj - jesli nie mamy go swiezego, pobieramy go
     * od razu w tle, zamiast kazac czekac do nastepnego przebiegu.
     *
     * @return czy import tego kraju wlasnie trwa
     */
    public boolean requestCountry(String kraj) {
        if (!ticketmaster.available() || !stale(kraj)) {
            return wKolejce.contains(kraj);
        }
        if (wKolejce.add(kraj)) {
            tlo.submit(() -> {
                try {
                    importCountries(List.of(kraj));
                } finally {
                    wKolejce.remove(kraj);
                }
            });
        }
        return true;
    }

    /** Czy import tego kraju wlasnie idzie w tle. */
    public boolean importing(String kraj) {
        return wKolejce.contains(kraj);
    }

    @PreDestroy
    void zatrzymaj() {
        tlo.shutdownNow();
    }

    /** Polska zawsze, potem kraje wybrane na kontach - od najczesciej wybieranego. */
    List<String> activeCountries() {
        List<String> kraje = new ArrayList<>(List.of(EventCountries.DOMYSLNY));
        for (String wybrany : users.eventCountriesInUse()) {
            String kraj = EventCountries.orDefault(wybrany);
            if (!kraje.contains(kraj)) {
                kraje.add(kraj);
            }
        }
        return kraje;
    }

    private boolean stale(String kraj) {
        LocalDateTime ostatni = ostatnioUdany.get(kraj);
        return ostatni == null || !ostatni.plus(coIle).isAfter(LocalDateTime.now(clock));
    }

    /** Import podanych krajow. Jeden naraz - drugi czeka, az skonczy sie pierwszy. */
    synchronized ImportStatus importCountries(List<String> kraje) {
        LocalDateTime startedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = today();

        int seen = 0;
        int removed = 0;
        List<String> bledy = new ArrayList<>();
        for (String kraj : kraje) {
            WynikKraju wynik = importCountry(kraj, startedAt, today);
            seen += wynik.widziane();
            removed += wynik.usuniete();
            if (wynik.blad() == null) {
                ostatnioUdany.put(kraj, LocalDateTime.now(clock));
            } else {
                bledy.add(kraje.size() > 1 ? kraj + ": " + wynik.blad() : wynik.blad());
            }
        }
        removed += removeOld(today.minusDays(DNI_PO_WYDARZENIU));

        boolean ok = bledy.isEmpty();
        if (ok) {
            log.info("Import wydarzen {}: {} z Ticketmastera, usunietych {}", kraje, seen, removed);
        }
        lastRun = new ImportStatus(LocalDateTime.now(clock), ok, seen, removed,
            ok ? null : String.join("; ", bledy));

        refreshPerformerTags(today);
        return lastRun;
    }

    private record WynikKraju(int widziane, int usuniete, String blad) { }

    /** Postep importu jednego kraju: ile zapytan juz poszlo i ile wydarzen widzielismy. */
    private static final class Postep {

        final String kraj;
        final int limit;
        int zapytan;
        int widziane;

        Postep(String kraj, int limit) {
            this.kraj = kraj;
            this.limit = limit;
        }
    }

    /** Limit zapytan na kraj sie skonczyl - to nie blad, tylko koniec pokrycia. */
    private static final class LimitWyczerpany extends RuntimeException {

        LimitWyczerpany() {
            super(null, null, false, false);
        }
    }

    private WynikKraju importCountry(String kraj, LocalDateTime startedAt, LocalDate today) {
        Postep postep = new Postep(kraj, limitNaKraj);
        LocalDate koniec = today.plusMonths(MIESIECY_NAPRZOD);
        Instant end = koniec.atStartOfDay(STREFA).toInstant();

        /* Do tego dnia (bez niego) wszystkie okresy przeszly w calosci. */
        LocalDate pokryteDo = today;
        try {
            LocalDate month = today;
            while (month.atStartOfDay(STREFA).toInstant().isBefore(end)) {
                LocalDate next = month.withDayOfMonth(1).plusMonths(1);
                Instant a = month.atStartOfDay(STREFA).toInstant();
                Instant b = next.atStartOfDay(STREFA).toInstant();
                importWindow(postep, a, b.isAfter(end) ? end : b, startedAt);
                pokryteDo = next.isAfter(koniec) ? koniec : next;
                month = next;
            }
        } catch (LimitWyczerpany e) {
            log.info("Import {}: wyczerpany limit {} zapytan - pokryte do {}", kraj, limitNaKraj, pokryteDo);
        } catch (IllegalStateException e) {
            /*
             * Przerwany import niczego nie usuwa. To, czego nie zdazyl
             * zobaczyc, nie zniknelo z Ticketmastera - po prostu nie doszlismy
             * do tego miesiaca.
             */
            log.warn("Import wydarzen {} przerwany po {} wydarzeniach: {}", kraj, postep.widziane, e.getMessage());
            return new WynikKraju(postep.widziane, 0, e.getMessage());
        }

        /*
         * Dzien zapasu na granicy pokrycia: okresy sa liczone po polskiemu, a
         * koncert o 23:30 w Londynie to juz nastepny dzien w Warszawie - moglby
         * nie wpasc w ostatni pokryty okres, a wygladalby na znikniety.
         */
        int usuniete = removeVanished(kraj, today, pokryteDo.minusDays(1), startedAt, postep.widziane);
        return new WynikKraju(postep.widziane, usuniete, null);
    }

    /**
     * Gatunki nowych wykonawcow z Last.fm - po imporcie, bo dopiero wtedy
     * wiadomo, kto gra. Blad tutaj nie psuje importu: wydarzenia juz sa,
     * brakuje najwyzej dokladniejszych gatunkow do "Dla ciebie".
     */
    private void refreshPerformerTags(LocalDate today) {
        try {
            performerTags.refresh(repository.upcomingPerformerNames(today));
        } catch (RuntimeException e) {
            log.warn("Nie udalo sie uzupelnic gatunkow wykonawcow: {}", e.getMessage());
        }
    }

    /**
     * Jeden okres - zwykle miesiac.
     *
     * Ticketmaster oddaje najwyzej 1000 wynikow na zapytanie. Gdy w okresie
     * jest wiecej, dzielimy go na polowy i pytamy o kazda osobno - az
     * kazdy kawalek sie zmiesci.
     */
    private void importWindow(Postep postep, Instant from, Instant to, LocalDateTime seenAt) {
        TicketmasterClient.Page page = fetch(postep, from, to, 0);

        if (page.totalElements() > TicketmasterClient.MAX_WYNIKOW_ZAPYTANIA
                && Duration.between(from, to).toHours() > 24) {
            Instant middle = from.plus(Duration.between(from, to).dividedBy(2))
                .truncatedTo(ChronoUnit.SECONDS);
            importWindow(postep, from, middle, seenAt);
            importWindow(postep, middle, to, seenAt);
            return;
        }

        postep.widziane += save(page.events(), postep.kraj, seenAt);
        for (int number = 1;
             !page.last() && number * TicketmasterClient.ROZMIAR_STRONY < TicketmasterClient.MAX_WYNIKOW_ZAPYTANIA;
             number++) {
            page = fetch(postep, from, to, number);
            postep.widziane += save(page.events(), postep.kraj, seenAt);
        }
    }

    private TicketmasterClient.Page fetch(Postep postep, Instant from, Instant to, int page) {
        if (postep.zapytan >= postep.limit) {
            throw new LimitWyczerpany();
        }
        postep.zapytan++;
        if (przerwaMs > 0) {
            try {
                Thread.sleep(przerwaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Import przerwany");
            }
        }
        return ticketmaster.events(postep.kraj, from, to, page);
    }

    /** Zapisuje jedna strone: nowe dopisuje, znane uaktualnia. Jedna transakcja na strone. */
    private int save(List<TicketmasterClient.Event> events, String kraj, LocalDateTime seenAt) {
        if (events.isEmpty()) {
            return 0;
        }

        return transactions.execute(status -> {
            List<String> ids = events.stream().map(TicketmasterClient.Event::externalId).toList();
            Map<String, MusicEvent> known = repository.findByExternalIdIn(ids).stream()
                .collect(Collectors.toMap(MusicEvent::getExternalId, Function.identity()));

            List<MusicEvent> zapisane = new ArrayList<>();
            for (TicketmasterClient.Event e : events) {
                String krajWydarzenia = e.countryCode() != null ? e.countryCode() : kraj;
                MusicEvent event = known.get(e.externalId());
                if (event == null) {
                    // Ten sam koncert mozemy juz miec z innego zrodla - wtedy Ticketmaster go przejmuje
                    event = merger.adopt(asExternal(e, krajWydarzenia)).orElseGet(() -> new MusicEvent(e.externalId()));
                    known.put(e.externalId(), event);
                }
                apply(event, e, seenAt);
                event.inCountry(krajWydarzenia);
                zapisane.add(repository.save(event));
            }
            // Ticketmaster nadpisal swoje dane - braki znow uzupelnione z innych zrodel
            merger.refill(zapisane);
            performerTags.saveLinks(events);
            performerTags.saveAbout(events);
            return events.size();
        });
    }

    private void apply(MusicEvent event, TicketmasterClient.Event e, LocalDateTime seenAt) {
        event.describe(e.name(), e.description(), e.status(), e.genre(), e.subGenre());
        TicketmasterClient.Organizer o = e.organizer() == null ? TicketmasterClient.Organizer.NONE : e.organizer();
        event.organizer(o.pleaseNote(), o.promoter(), o.priceMin(), o.priceMax(), o.priceCurrency(),
            o.ageRestricted(), o.salesStart(), o.accessibility());
        event.schedule(e.date(), e.time());
        event.place(e.venueExternalId(), e.venueName(), e.city(), cityKey(e.city()),
            e.address(), e.latitude(), e.longitude());
        event.link(e.ticketUrl(), e.imageUrl(), e.thumbUrl());
        event.groupAs(seriesKey(e.name(), e.venueExternalId(), e.venueName()));
        event.replacePerformers(e.performers().stream()
            .map(p -> new EventPerformer(p.externalId(), p.name()))
            .toList());
        event.markSeen(seenAt);
    }

    /**
     * Usuwa nadchodzace wydarzenia, ktorych pelny import juz nie zobaczyl -
     * Ticketmaster je wycofal.
     *
     * Tylko w jednym kraju i tylko w zakresie dat, ktory import tego kraju
     * przeszedl w calosci - za granica limitu zapytan niczego nie widzielismy,
     * wiec niczego nie wolno tam uznac za znikniete.
     *
     * Dwa bezpieczniki, bo usuniecie jest nieodwracalne, a Ticketmaster
     * potrafi chwilowo oddac pusta albo okrojona odpowiedz: przy zerze
     * wynikow nie ruszamy niczego, i tak samo, gdy zniknac mialoby wiecej,
     * niz zobaczylismy. Wtedy lepiej pokazac przez kilka godzin cos
     * nieaktualnego, niz wyczyscic cala zakladke.
     *
     * Wydarzenia, na ktore ktos sie zapisal albo pod ktorymi sa posty, nie sa
     * kasowane, tylko oznaczane jako wycofane: znikaja z listy, ale zapisani
     * widza w zakladce "Moje", co sie stalo, a odnosnik z posta prowadzi na
     * strone z wyjasnieniem. Inaczej zapis przepadalby bez slowa.
     */
    private int removeVanished(String kraj, LocalDate today, LocalDate until, LocalDateTime startedAt, int seen) {
        return transactions.execute(status -> {
            List<MusicEvent> vanished = repository.vanished(kraj, today, until, startedAt);
            if (vanished.isEmpty()) {
                return 0;
            }
            /*
             * Gdy zniknelo wiecej, niz zobaczylismy - czyli ponad polowa tego,
             * co bylo - to raczej chwilowa awaria po stronie Ticketmastera niz
             * masowe odwolania.
             */
            if (seen == 0 || vanished.size() > seen) {
                log.warn("Import {} zobaczyl {} wydarzen, a zniknac mialoby {} - niczego nie usuwam",
                    kraj, seen, vanished.size());
                return 0;
            }
            List<Long> ids = vanished.stream().map(MusicEvent::getId).toList();
            Set<Long> zapisane = new HashSet<>(participations.eventsWithParticipants(ids));
            // Posty pod wydarzeniem tez: strona ma wyjasnic, co sie stalo, a nie zniknac
            zapisane.addAll(repository.withPosts(ids));

            List<MusicEvent> doUsuniecia = new ArrayList<>();
            for (MusicEvent event : vanished) {
                // Inne zrodlo dalej je pokazuje - przechodzi na nie, zamiast znikac
                if (merger.rescue(event, startedAt)) {
                    continue;
                }
                if (zapisane.contains(event.getId())) {
                    event.withdraw(startedAt);
                } else {
                    doUsuniecia.add(event);
                }
            }
            repository.deleteAll(doUsuniecia);
            return doUsuniecia.size();
        });
    }

    /**
     * Dawno minione - razem z zapisami, bo po miesiacu nikomu juz do niczego nie sluza.
     * Posty pod nimi zostaja jako zwykle wpisy: odnosnik czysci baza (ON DELETE SET NULL).
     */
    private int removeOld(LocalDate before) {
        return transactions.execute(status -> {
            List<MusicEvent> old = repository.findByStartDateBefore(before);
            if (old.isEmpty()) {
                return 0;
            }
            participations.deleteByEventIds(old.stream().map(MusicEvent::getId).toList());
            repository.deleteAll(old);
            return old.size();
        });
    }

    /** Wydarzenie Ticketmastera w postaci, ktora porownuje {@link EventMerger}. */
    static ExternalEvent asExternal(TicketmasterClient.Event e, String kraj) {
        return new ExternalEvent(com.musicclubapp.entity.EventSource.TICKETMASTER, e.externalId(), e.ticketUrl(),
            e.name(), e.status(), e.date(), e.time(), e.venueName(), e.city(), kraj, e.address(), e.latitude(),
            e.longitude(), e.ticketUrl(), e.imageUrl(), e.description(),
            e.performers().stream().map(TicketmasterClient.Performer::name).toList());
    }

    /** Dzisiejsza data w Polsce - serwer liczy czas w UTC, a o polnocy to juz robi roznice. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(STREFA));
    }

    /**
     * Miasto w jednej postaci: male litery, bez polskich znakow, bez spacji
     * na koncach. "Łódź " i "Lodz" daja "lodz".
     *
     * "ł" trzeba zamienic osobno. Normalizer rozklada "ó" na "o" i kreske,
     * ktora potem zdejmujemy - ale "ł" jest w Unicode osobna litera, a nie
     * "l" z doklejonym znakiem, wiec sam Normalizer jej nie ruszy.
     */
    static String cityKey(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String ascii = Normalizer.normalize(city.strip(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('ł', 'l')
            .replace('Ł', 'L');
        String key = ascii.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return TE_SAME_MIASTA.getOrDefault(key, key);
    }

    /** Co laczy terminy jednej serii: nazwa i miejsce. */
    static String seriesKey(String name, String venueExternalId, String venueName) {
        String nazwa = name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        String miejsce = venueExternalId != null
            ? venueExternalId
            : venueName == null ? "" : venueName.toLowerCase(Locale.ROOT).strip();
        String key = nazwa + "|" + miejsce;
        return key.length() <= 400 ? key : key.substring(0, 400);
    }
}
