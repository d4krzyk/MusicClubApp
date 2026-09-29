package com.musicclubapp.service;

import com.musicclubapp.dto.AttendeeResponse;
import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventCityResponse;
import com.musicclubapp.dto.EventDateResponse;
import com.musicclubapp.dto.EventDetailsResponse;
import com.musicclubapp.dto.EventReasonResponse;
import com.musicclubapp.dto.EventView;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.EventCardRow;
import com.musicclubapp.repository.EventCountRow;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.ParticipationCountRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Odczyt wydarzen: trzy widoki listy, strona wydarzenia, miasta. */
@Service
public class EventService {

    /** Ile nazwisk ze skladu idzie na karte. Wiecej i tak sie nie zmiesci. */
    private static final int WYKONAWCOW_NA_KARCIE = 5;

    /** Ile innych terminow pokazujemy pod wydarzeniem. */
    private static final int INNYCH_TERMINOW = 30;

    /** Ilu uczestnikow widac od razu na stronie wydarzenia; reszta pod "pokaz wszystkich". */
    static final int UCZESTNIKOW_NA_STRONIE = 12;

    /** Dluzsza fraza to juz nie wyszukiwanie, tylko wklejony tekst. */
    private static final int MAX_FRAZA = 100;

    private final MusicEventRepository repository;
    private final EventParticipationRepository participations;
    private final UserRepository users;
    private final EventImportService importer;
    private final EventMatchService matcher;
    private final EventParticipationService participationService;
    private final PerformerTagService performerTags;

    public EventService(MusicEventRepository repository,
                        EventParticipationRepository participations,
                        UserRepository users,
                        EventImportService importer,
                        EventMatchService matcher,
                        EventParticipationService participationService,
                        PerformerTagService performerTags) {
        this.repository = repository;
        this.participations = participations;
        this.users = users;
        this.importer = importer;
        this.matcher = matcher;
        this.participationService = participationService;
        this.performerTags = performerTags;
    }

    /**
     * Jedna ze trzech list.
     *
     * @param city   klucz miasta albo pusty - wszystkie miasta
     * @param phrase szukany tekst w nazwie, miejscu albo skladzie; pusty - bez szukania
     */
    @Transactional(readOnly = true)
    public Page<EventCardResponse> list(EventView view, String city, String phrase,
                                        Pageable pageable, String viewer) {
        return switch (view) {
            case FOR_YOU -> forYou(city, phrase, pageable, viewer);
            case MINE -> mine(pageable, viewer);
            case UPCOMING -> upcoming(city, phrase, pageable, viewer);
        };
    }

    /** Nadchodzace od najblizszego, jedna karta na serie. */
    private Page<EventCardResponse> upcoming(String city, String phrase, Pageable pageable, String viewer) {
        LocalDate today = importer.today();
        String cityKey = cityKey(city);
        String pattern = likePattern(phrase);

        long total = repository.countSeries(today, cityKey, pattern);
        if (total == 0) {
            return Page.empty(pageable);
        }

        List<EventCardRow> rows = repository.firstOfEachSeries(
            today, cityKey, pattern, pageable.getPageSize(), pageable.getOffset());

        Map<Long, Long> moreDates = rows.stream()
            .collect(Collectors.toMap(EventCardRow::getId, row -> row.getDatesCount() - 1, (a, b) -> a));
        List<Long> ids = rows.stream().map(EventCardRow::getId).toList();

        return new PageImpl<>(cards(ids, moreDates, Map.of(), viewer), pageable, total);
    }

    /**
     * "Dla ciebie": tylko to, co ma cos wspolnego z profilem, od najlepiej
     * pasujacego. Liczone w Javie, a nie w SQL - punkty biora sie z kilku
     * zrodel naraz (sklad, tagi Last.fm, znajomi), a nadchodzacych wydarzen
     * jest kilkaset, nie miliony.
     */
    private Page<EventCardResponse> forYou(String city, String phrase, Pageable pageable, String viewer) {
        LocalDate today = importer.today();
        EventMatchService.Taste taste = matcher.tasteOf(viewer);

        Map<Long, Long> friendCounts = taste.friendIds().isEmpty()
            ? Map.of()
            : toMap(participations.countFriendsUpcoming(taste.friendIds(), today));

        String szukane = phrase == null ? "" : phrase.strip();
        if (szukane.length() > MAX_FRAZA) {
            szukane = szukane.substring(0, MAX_FRAZA);
        }
        List<EventMatchService.Ranked> ranked =
            matcher.rank(taste, today, cityKey(city), szukane, friendCounts);

        int from = (int) Math.min(pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());
        List<EventMatchService.Ranked> page = ranked.subList(from, to);

        Map<Long, Long> moreDates = new HashMap<>();
        Map<Long, List<EventReasonResponse>> reasons = new HashMap<>();
        for (EventMatchService.Ranked r : page) {
            moreDates.put(r.eventId(), r.moreDates());
            reasons.put(r.eventId(), r.match().reasons());
        }
        List<Long> ids = page.stream().map(EventMatchService.Ranked::eventId).toList();

        return new PageImpl<>(cards(ids, moreDates, reasons, viewer), pageable, ranked.size());
    }

    /** Moje zapisy - kazdy termin osobno, bo zapisuje sie na konkretny dzien. */
    private Page<EventCardResponse> mine(Pageable pageable, String viewer) {
        Page<EventParticipation> page = participations.mineUpcoming(viewer, importer.today(), pageable);
        List<Long> ids = page.getContent().stream().map(p -> p.getEvent().getId()).toList();
        return new PageImpl<>(cards(ids, Map.of(), Map.of(), viewer), pageable, page.getTotalElements());
    }

    /**
     * Karty dla podanych wydarzen, w podanej kolejnosci, razem z moim
     * zapisem i licznikami - kilka zapytan na cala strone, a nie na karte.
     */
    private List<EventCardResponse> cards(List<Long> ids, Map<Long, Long> moreDates,
                                          Map<Long, List<EventReasonResponse>> reasons, String viewer) {
        if (ids.isEmpty()) {
            return List.of();
        }

        /*
         * JOIN FETCH nie gwarantuje kolejnosci, a kolejnosc jest tu cala
         * trescia listy - wiec ustawiamy ja z powrotem wedlug ids.
         */
        Map<Long, MusicEvent> byId = repository.findWithPerformers(ids).stream()
            .collect(Collectors.toMap(MusicEvent::getId, Function.identity(), (a, b) -> a));

        Map<Long, long[]> counts = new HashMap<>();
        for (ParticipationCountRow row : participations.countByStatus(ids)) {
            long[] c = counts.computeIfAbsent(row.getEventId(), k -> new long[2]);
            c[row.getStatus() == ParticipationStatus.GOING ? 0 : 1] = row.getTotal();
        }

        Map<Long, ParticipationStatus> mine = new HashMap<>();
        Set<Long> friendIds = Set.of();
        if (viewer != null) {
            for (EventParticipation p : participations.findMineAmong(viewer, ids)) {
                mine.put(p.getEvent().getId(), p.getStatus());
            }
            friendIds = new HashSet<>(users.friendIdsOf(viewer));
        }
        Map<Long, Long> friends = friendIds.isEmpty()
            ? Map.of()
            : toMap(participations.countFriends(ids, friendIds));

        return ids.stream()
            .filter(byId::containsKey)
            .map(id -> {
                MusicEvent e = byId.get(id);
                long[] c = counts.getOrDefault(id, new long[2]);
                return new EventCardResponse(
                    e.getId(),
                    e.getName(),
                    e.getStartDate(),
                    e.getStartTime(),
                    e.getStatus(),
                    e.getVenueName(),
                    e.getCity(),
                    e.getCityKey(),
                    e.getThumbUrl(),
                    e.getPerformers().stream()
                        .limit(WYKONAWCOW_NA_KARCIE)
                        .map(EventPerformer::getName)
                        .toList(),
                    e.getGenre(),
                    Math.max(0, moreDates.getOrDefault(id, 0L)),
                    mine.get(id),
                    c[0],
                    c[1],
                    friends.getOrDefault(id, 0L),
                    reasons.getOrDefault(id, List.of()),
                    e.isWithdrawn());
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public EventDetailsResponse details(Long id, String viewer) {
        MusicEvent event = repository.findByIdWithPerformers(id)
            .orElseThrow(() -> new NoSuchElementFoundException("event", id));

        LocalDate today = importer.today();
        List<EventDateResponse> otherDates = repository
            .upcomingInSeries(event.getSeriesKey(), today, PageRequest.of(0, INNYCH_TERMINOW + 1))
            .stream()
            .filter(other -> !other.getId().equals(event.getId()))
            .limit(INNYCH_TERMINOW)
            .map(other -> new EventDateResponse(
                other.getId(), other.getStartDate(), other.getStartTime(), other.getStatus()))
            .toList();

        List<String> performers = event.getPerformers().stream().map(EventPerformer::getName).toList();

        EventMatchService.Taste taste = matcher.tasteOf(viewer);
        long friends = taste.friendIds().isEmpty()
            ? 0
            : toMap(participations.countFriends(List.of(id), taste.friendIds())).getOrDefault(id, 0L);
        List<EventReasonResponse> reasons = matcher.matchOne(taste, id, performers, friends).reasons();

        List<AttendeeResponse> attendees = participationService
            .attendees(id, viewer, PageRequest.of(0, UCZESTNIKOW_NA_STRONIE))
            .getContent();

        return new EventDetailsResponse(
            event.getId(),
            event.getName(),
            event.getStartDate(),
            event.getStartTime(),
            event.getStatus(),
            event.getDescription(),
            event.getTicketUrl(),
            event.getImageUrl(),
            event.getVenueName(),
            event.getCity(),
            event.getCityKey(),
            event.getAddress(),
            event.getLatitude(),
            event.getLongitude(),
            event.getGenre(),
            event.getSubGenre(),
            performers,
            otherDates,
            event.getStartDate().isBefore(today),
            event.isWithdrawn(),
            participationService.summary(id, viewer),
            friends,
            reasons,
            attendees);
    }

    /**
     * Miasta do filtra, stan importu i to, co decyduje o domyslnym widoku:
     * czy profil ma czym sie dopasowac i ile mam zapisow.
     *
     * Szczegoly importu - z komunikatem bledu - dostaje tylko administrator.
     */
    @Transactional(readOnly = true)
    public EventsInfoResponse info(boolean admin, String viewer) {
        LocalDate today = importer.today();
        List<EventCityResponse> cities = repository.cities(today).stream()
            .map(row -> new EventCityResponse(row.getCityKey(), row.getCityName(), row.getEvents()))
            .toList();

        EventsInfoResponse.ImportInfo lastImport = null;
        EventImportService.ImportStatus run = importer.lastRun();
        if (admin && run != null) {
            lastImport = new EventsInfoResponse.ImportInfo(
                run.finishedAt(), run.success(), run.events(), run.removed(), run.error());
        }

        EventMatchService.Taste taste = matcher.tasteOf(viewer);
        boolean hasTaste = !taste.artists().isEmpty() || !taste.trackArtists().isEmpty();

        return new EventsInfoResponse(importer.available(), cities, hasTaste,
            participations.countMineUpcoming(viewer, today), performerTags.available(), lastImport);
    }

    private static Map<Long, Long> toMap(List<EventCountRow> rows) {
        return rows.stream().collect(Collectors.toMap(EventCountRow::getEventId, EventCountRow::getTotal));
    }

    private static String cityKey(String city) {
        return city == null || city.isBlank() ? "" : EventImportService.cityKey(city);
    }

    /**
     * Zamienia wpisany tekst na wzorzec do LIKE.
     *
     * Znaki % i _ maja w LIKE specjalne znaczenie ("cokolwiek" i "jeden
     * dowolny znak"). Bez poprzedzenia ich ukosnikiem wpisanie "_" znalazloby
     * kazde wydarzenie, a "100%" - nie to, czego ktos szukal.
     */
    static String likePattern(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return "";
        }
        String trimmed = phrase.strip();
        if (trimmed.length() > MAX_FRAZA) {
            trimmed = trimmed.substring(0, MAX_FRAZA);
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
