package com.musicclubapp.repository;

import com.musicclubapp.entity.MusicEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Wydarzenia pobrane z Ticketmastera. */
public interface MusicEventRepository extends JpaRepository<MusicEvent, Long> {

    /**
     * Co z tej strony importu juz mamy - jednym zapytaniem, a nie jednym na wydarzenie.
     *
     * Sklad dociagamy od razu: import porownuje go z nowym, a bez JOIN FETCH
     * kazde porownanie bylo osobnym zapytaniem - dwiescie na strone.
     */
    @Query("SELECT e FROM MusicEvent e LEFT JOIN FETCH e.performers WHERE e.externalId IN :ids")
    List<MusicEvent> findByExternalIdIn(@Param("ids") Collection<String> externalIds);

    @Query("SELECT e FROM MusicEvent e LEFT JOIN FETCH e.performers WHERE e.id IN :ids")
    List<MusicEvent> findWithPerformers(@Param("ids") Collection<Long> ids);

    @Query("SELECT e FROM MusicEvent e LEFT JOIN FETCH e.performers WHERE e.id = :id")
    Optional<MusicEvent> findByIdWithPerformers(@Param("id") Long id);

    /*
     * Wspolny warunek listy: od dzis, opcjonalnie jedno miasto i fraza.
     *
     * Pusty tekst zamiast NULL-a oznacza "bez filtra". Warunek ":city IS NULL"
     * PostgreSQL odrzuca, gdy parametr przychodzi pusty - nie umie wtedy
     * ustalic jego typu ("could not determine data type of parameter").
     */
    String FILTR = """
        e.start_date >= :today
        AND (:city = '' OR e.city_key = :city)
        AND (:pattern = ''
             OR LOWER(e.name) LIKE :pattern ESCAPE '\\'
             OR LOWER(e.venue_name) LIKE :pattern ESCAPE '\\'
             OR EXISTS (SELECT 1 FROM music_event_performers p
                         WHERE p.event_id = e.id
                           AND LOWER(p.name) LIKE :pattern ESCAPE '\\'))
        """;

    /**
     * Pierwszy nadchodzacy termin kazdej serii i liczba jej terminow.
     *
     * ROW_NUMBER() numeruje terminy w obrebie serii od najblizszego, wiec
     * "nr = 1" to jeden wiersz na serie. COUNT(*) OVER liczy terminy tej samej
     * serii bez zwijania wierszy - dlatego obie rzeczy wychodza z jednego
     * przejscia po tabeli.
     */
    /*
     * "WHERE\s" zamiast "WHERE " - blok tekstowy w Javie obcina spacje na koncach
     * linii, wiec zwykla spacja znikala i wychodzilo "WHEREe.start_date".
     */
    @Query(value = """
        SELECT s.id AS id, s.datesCount AS datesCount
          FROM (SELECT e.id, e.name, e.start_date, e.start_time,
                       ROW_NUMBER() OVER (PARTITION BY e.series_key
                                          ORDER BY e.start_date, e.start_time, e.id) AS nr,
                       COUNT(*) OVER (PARTITION BY e.series_key) AS datesCount
                  FROM music_events e
                 WHERE\s""" + FILTR + """
               ) s
         WHERE s.nr = 1
         ORDER BY s.start_date, s.start_time NULLS LAST, s.name, s.id
         LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<EventCardRow> firstOfEachSeries(@Param("today") LocalDate today,
                                         @Param("city") String city,
                                         @Param("pattern") String pattern,
                                         @Param("limit") int limit,
                                         @Param("offset") long offset);

    /** Ile serii pasuje do filtra - do licznika "pokazano X z Y". */
    @Query(value = "SELECT COUNT(DISTINCT e.series_key) FROM music_events e WHERE " + FILTR,
        nativeQuery = true)
    long countSeries(@Param("today") LocalDate today,
                     @Param("city") String city,
                     @Param("pattern") String pattern);

    /** Nadchodzace terminy jednej serii - na stronie wydarzenia. */
    @Query("""
        SELECT e FROM MusicEvent e
         WHERE e.seriesKey = :seriesKey AND e.startDate >= :today
         ORDER BY e.startDate, e.startTime NULLS LAST, e.id
        """)
    List<MusicEvent> upcomingInSeries(@Param("seriesKey") String seriesKey,
                                      @Param("today") LocalDate today,
                                      Pageable limit);

    /** Miasta, w ktorych cos sie dzieje, od najbardziej ruchliwego. */
    @Query("""
        SELECT e.cityKey AS cityKey, MAX(e.city) AS cityName, COUNT(DISTINCT e.seriesKey) AS events
          FROM MusicEvent e
         WHERE e.startDate >= :today AND e.cityKey IS NOT NULL
         GROUP BY e.cityKey
         ORDER BY COUNT(DISTINCT e.seriesKey) DESC, e.cityKey
        """)
    List<EventCityRow> cities(@Param("today") LocalDate today);

    /** Nadchodzace wydarzenia, ktorych ostatni pelny import juz nie widzial. */
    List<MusicEvent> findByStartDateGreaterThanEqualAndLastSeenAtBefore(LocalDate today,
                                                                         LocalDateTime seenBefore);

    /** Wydarzenia, ktore dawno sie odbyly. */
    List<MusicEvent> findByStartDateBefore(LocalDate date);

    long countByStartDateGreaterThanEqual(LocalDate today);
}
