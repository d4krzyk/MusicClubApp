package com.musicclubapp.service;

import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventCityResponse;
import com.musicclubapp.dto.EventDateResponse;
import com.musicclubapp.dto.EventDetailsResponse;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.EventCardRow;
import com.musicclubapp.repository.MusicEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Odczyt wydarzen: lista, strona wydarzenia, miasta. */
@Service
public class EventService {

    /** Ile nazwisk ze skladu idzie na karte. Wiecej i tak sie nie zmiesci. */
    private static final int WYKONAWCOW_NA_KARCIE = 5;

    /** Ile innych terminow pokazujemy pod wydarzeniem. */
    private static final int INNYCH_TERMINOW = 30;

    /** Dluzsza fraza to juz nie wyszukiwanie, tylko wklejony tekst. */
    private static final int MAX_FRAZA = 100;

    private final MusicEventRepository repository;
    private final EventImportService importer;

    public EventService(MusicEventRepository repository, EventImportService importer) {
        this.repository = repository;
        this.importer = importer;
    }

    /**
     * Nadchodzace wydarzenia od najblizszego, jedna karta na serie.
     *
     * @param city   klucz miasta albo pusty - wszystkie miasta
     * @param phrase szukany tekst w nazwie, miejscu albo skladzie; pusty - bez szukania
     */
    @Transactional(readOnly = true)
    public Page<EventCardResponse> list(String city, String phrase, Pageable pageable) {
        LocalDate today = importer.today();
        String cityKey = city == null || city.isBlank() ? "" : EventImportService.cityKey(city);
        String pattern = likePattern(phrase);

        long total = repository.countSeries(today, cityKey, pattern);
        if (total == 0) {
            return Page.empty(pageable);
        }

        List<EventCardRow> rows = repository.firstOfEachSeries(
            today, cityKey, pattern, pageable.getPageSize(), pageable.getOffset());

        /*
         * Druga runda po pelne encje ze skladem. Zapytanie z oknami zwraca
         * tylko identyfikatory - w odpowiedniej kolejnosci - a JOIN FETCH
         * kolejnosci nie gwarantuje, wiec ustawiamy ja z powrotem recznie.
         */
        Map<Long, MusicEvent> byId = repository
            .findWithPerformers(rows.stream().map(EventCardRow::getId).toList())
            .stream()
            .collect(Collectors.toMap(MusicEvent::getId, Function.identity(), (a, b) -> a));

        List<EventCardResponse> cards = rows.stream()
            .filter(row -> byId.containsKey(row.getId()))
            .map(row -> toCard(byId.get(row.getId()), row.getDatesCount() - 1))
            .toList();

        return new PageImpl<>(cards, pageable, total);
    }

    @Transactional(readOnly = true)
    public EventDetailsResponse details(Long id) {
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
            event.getPerformers().stream().map(EventPerformer::getName).toList(),
            otherDates,
            event.getStartDate().isBefore(today));
    }

    /**
     * Miasta do filtra i stan importu.
     *
     * Szczegoly importu - z komunikatem bledu - dostaje tylko administrator.
     * Reszcie wystarczy wiedziec, czy wydarzenia w ogole sa wlaczone.
     */
    @Transactional(readOnly = true)
    public EventsInfoResponse info(boolean admin) {
        List<EventCityResponse> cities = repository.cities(importer.today()).stream()
            .map(row -> new EventCityResponse(row.getCityKey(), row.getCityName(), row.getEvents()))
            .toList();

        EventsInfoResponse.ImportInfo lastImport = null;
        EventImportService.ImportStatus run = importer.lastRun();
        if (admin && run != null) {
            lastImport = new EventsInfoResponse.ImportInfo(
                run.finishedAt(), run.success(), run.events(), run.removed(), run.error());
        }

        return new EventsInfoResponse(importer.available(), cities, lastImport);
    }

    private EventCardResponse toCard(MusicEvent event, long moreDates) {
        return new EventCardResponse(
            event.getId(),
            event.getName(),
            event.getStartDate(),
            event.getStartTime(),
            event.getStatus(),
            event.getVenueName(),
            event.getCity(),
            event.getCityKey(),
            event.getThumbUrl(),
            event.getPerformers().stream()
                .limit(WYKONAWCOW_NA_KARCIE)
                .map(EventPerformer::getName)
                .toList(),
            event.getGenre(),
            Math.max(0, moreDates));
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
