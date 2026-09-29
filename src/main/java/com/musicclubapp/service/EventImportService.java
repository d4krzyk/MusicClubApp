package com.musicclubapp.service;

import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.repository.MusicEventRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Wszystkie wydarzenia sa w Polsce, wiec "dzis" liczymy po polskiemu. */
    public static final ZoneId STREFA = ZoneId.of("Europe/Warsaw");

    private static final String KRAJ = "PL";

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
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Przerwa miedzy zapytaniami - Ticketmaster pozwala na 5 na sekunde. */
    private final long przerwaMs;

    /** Co ile powtarzamy UDANY import. Po nieudanym probujemy przy najblizszym sprawdzeniu. */
    private final Duration coIle;

    /** Wynik ostatniego przebiegu. W pamieci - po restarcie i tak zaraz bedzie nowy. */
    private volatile ImportStatus lastRun;

    @Autowired
    public EventImportService(TicketmasterClient ticketmaster,
                              MusicEventRepository repository,
                              PlatformTransactionManager transactionManager,
                              Clock clock,
                              @Value("${app.events.import.pause-ms:250}") long przerwaMs,
                              @Value("${app.events.import.interval-ms:21600000}") long coIleMs) {
        this.ticketmaster = ticketmaster;
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.przerwaMs = przerwaMs;
        this.coIle = Duration.ofMillis(coIleMs);
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
     * sprawdza co kwadrans, czy pora na kolejny import.
     *
     * Po UDANYM imporcie nastepny idzie dopiero po 6 godzinach. Po NIEUDANYM -
     * przy najblizszym sprawdzeniu. Pierwsza wersja miala po prostu "co
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
        ImportStatus poprzedni = lastRun;
        boolean niedawnoUdany = poprzedni != null && poprzedni.success()
            && poprzedni.finishedAt().plus(coIle).isAfter(LocalDateTime.now(clock));
        if (!niedawnoUdany) {
            runImport();
        }
    }

    /** Jeden pelny przebieg importu. */
    public synchronized ImportStatus runImport() {
        LocalDateTime startedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        LocalDate today = today();
        long upcomingBefore = repository.countByStartDateGreaterThanEqual(today);

        Instant from = today.atStartOfDay(STREFA).toInstant();
        Instant end = today.plusMonths(MIESIECY_NAPRZOD).atStartOfDay(STREFA).toInstant();

        int seen = 0;
        try {
            LocalDate month = today;
            while (month.atStartOfDay(STREFA).toInstant().isBefore(end)) {
                LocalDate next = month.withDayOfMonth(1).plusMonths(1);
                Instant a = month.atStartOfDay(STREFA).toInstant();
                Instant b = next.atStartOfDay(STREFA).toInstant();
                seen += importWindow(a, b.isAfter(end) ? end : b, startedAt);
                month = next;
            }
        } catch (IllegalStateException e) {
            /*
             * Przerwany import niczego nie usuwa. To, czego nie zdazyl
             * zobaczyc, nie zniknelo z Ticketmastera - po prostu nie doszlismy
             * do tego miesiaca.
             */
            log.warn("Import wydarzen przerwany po {} wydarzeniach: {}", seen, e.getMessage());
            lastRun = new ImportStatus(LocalDateTime.now(clock), false, seen, 0, e.getMessage());
            return lastRun;
        }

        int removed = removeVanished(today, startedAt, seen, upcomingBefore);
        removed += removeOld(today.minusDays(DNI_PO_WYDARZENIU));

        log.info("Import wydarzen: {} z Ticketmastera, usunietych {}", seen, removed);
        lastRun = new ImportStatus(LocalDateTime.now(clock), true, seen, removed, null);
        return lastRun;
    }

    /**
     * Jeden okres - zwykle miesiac.
     *
     * Ticketmaster oddaje najwyzej 1000 wynikow na zapytanie. Gdy w okresie
     * jest wiecej, dzielimy go na polowy i pytamy o kazda osobno - az
     * kazdy kawalek sie zmiesci.
     */
    private int importWindow(Instant from, Instant to, LocalDateTime seenAt) {
        TicketmasterClient.Page page = fetch(from, to, 0);

        if (page.totalElements() > TicketmasterClient.MAX_WYNIKOW_ZAPYTANIA
                && Duration.between(from, to).toHours() > 24) {
            Instant middle = from.plus(Duration.between(from, to).dividedBy(2))
                .truncatedTo(ChronoUnit.SECONDS);
            return importWindow(from, middle, seenAt) + importWindow(middle, to, seenAt);
        }

        int count = save(page.events(), seenAt);
        for (int number = 1;
             !page.last() && number * TicketmasterClient.ROZMIAR_STRONY < TicketmasterClient.MAX_WYNIKOW_ZAPYTANIA;
             number++) {
            page = fetch(from, to, number);
            count += save(page.events(), seenAt);
        }
        return count;
    }

    private TicketmasterClient.Page fetch(Instant from, Instant to, int page) {
        if (przerwaMs > 0) {
            try {
                Thread.sleep(przerwaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Import przerwany");
            }
        }
        return ticketmaster.events(KRAJ, from, to, page);
    }

    /** Zapisuje jedna strone: nowe dopisuje, znane uaktualnia. Jedna transakcja na strone. */
    private int save(List<TicketmasterClient.Event> events, LocalDateTime seenAt) {
        if (events.isEmpty()) {
            return 0;
        }

        return transactions.execute(status -> {
            List<String> ids = events.stream().map(TicketmasterClient.Event::externalId).toList();
            Map<String, MusicEvent> known = repository.findByExternalIdIn(ids).stream()
                .collect(Collectors.toMap(MusicEvent::getExternalId, Function.identity()));

            for (TicketmasterClient.Event e : events) {
                MusicEvent event = known.computeIfAbsent(e.externalId(), MusicEvent::new);
                apply(event, e, seenAt);
                repository.save(event);
            }
            return events.size();
        });
    }

    private void apply(MusicEvent event, TicketmasterClient.Event e, LocalDateTime seenAt) {
        event.describe(e.name(), e.description(), e.status(), e.genre(), e.subGenre());
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
     * Dwa bezpieczniki, bo usuniecie jest nieodwracalne, a Ticketmaster
     * potrafi chwilowo oddac pusta albo okrojona odpowiedz: przy zerze
     * wynikow nie ruszamy niczego, i tak samo, gdy wynikow jest mniej niz
     * polowa tego, co bylo. Wtedy lepiej pokazac przez kilka godzin cos
     * nieaktualnego, niz wyczyscic cala zakladke.
     */
    private int removeVanished(LocalDate today, LocalDateTime startedAt, int seen, long upcomingBefore) {
        if (seen == 0 || seen * 2L < upcomingBefore) {
            log.warn("Import zobaczyl {} wydarzen przy {} wczesniej - niczego nie usuwam", seen, upcomingBefore);
            return 0;
        }
        return transactions.execute(status -> {
            List<MusicEvent> vanished =
                repository.findByStartDateGreaterThanEqualAndLastSeenAtBefore(today, startedAt);
            repository.deleteAll(vanished);
            return vanished.size();
        });
    }

    private int removeOld(LocalDate before) {
        return transactions.execute(status -> {
            List<MusicEvent> old = repository.findByStartDateBefore(before);
            repository.deleteAll(old);
            return old.size();
        });
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
