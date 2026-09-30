package com.musicclubapp.repository;

import com.musicclubapp.entity.EventParticipation;
import com.musicclubapp.entity.ParticipationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Zapisy na wydarzenia: kto zainteresowany, kto idzie. */
public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    @Query("""
        SELECT p FROM EventParticipation p
         WHERE p.event.id = :eventId AND p.user.username = :username
        """)
    Optional<EventParticipation> findMine(@Param("eventId") Long eventId,
                                          @Param("username") String username);

    /** Moje zapisy na wydarzenia z tej strony listy - jednym zapytaniem. */
    @Query("""
        SELECT p FROM EventParticipation p
         WHERE p.user.username = :username AND p.event.id IN :eventIds
        """)
    List<EventParticipation> findMineAmong(@Param("username") String username,
                                           @Param("eventIds") Collection<Long> eventIds);

    /** Liczniki "idzie" i "zainteresowanych" dla wielu wydarzen naraz. */
    @Query("""
        SELECT p.event.id AS eventId, p.status AS status, COUNT(p) AS total
          FROM EventParticipation p
         WHERE p.event.id IN :eventIds
         GROUP BY p.event.id, p.status
        """)
    List<ParticipationCountRow> countByStatus(@Param("eventIds") Collection<Long> eventIds);

    /** Ilu znajomych zapisalo sie na kazde z wydarzen - w dowolnym stanie. */
    @Query("""
        SELECT p.event.id AS eventId, COUNT(p) AS total
          FROM EventParticipation p
         WHERE p.event.id IN :eventIds AND p.user.id IN :friendIds
         GROUP BY p.event.id
        """)
    List<EventCountRow> countFriends(@Param("eventIds") Collection<Long> eventIds,
                                     @Param("friendIds") Collection<Long> friendIds);

    /** To samo dla wszystkich nadchodzacych naraz - do ukladania listy "Dla ciebie". */
    @Query("""
        SELECT p.event.id AS eventId, COUNT(p) AS total
          FROM EventParticipation p
         WHERE p.user.id IN :friendIds AND p.event.startDate >= :today
         GROUP BY p.event.id
        """)
    List<EventCountRow> countFriendsUpcoming(@Param("friendIds") Collection<Long> friendIds,
                                             @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(p) FROM EventParticipation p
         WHERE p.user.username = :username AND p.event.startDate >= :today
        """)
    long countMineUpcoming(@Param("username") String username, @Param("today") LocalDate today);

    /**
     * Lista uczestnikow: tylko "ide", bez ukrytych - chyba ze to sam
     * ogladajacy, ktory widzi siebie zawsze. Najpierw on, potem znajomi,
     * potem reszta w kolejnosci zapisu.
     */
    @Query(value = """
        SELECT p FROM EventParticipation p JOIN FETCH p.user u
         WHERE p.event.id = :eventId
           AND p.status = com.musicclubapp.entity.ParticipationStatus.GOING
           AND (p.hidden = false OR u.username = :viewer)
           AND u.id NOT IN :hiddenIds
         ORDER BY CASE WHEN u.username = :viewer THEN 0
                       WHEN u.id IN :friendIds THEN 1
                       ELSE 2 END,
                  p.createdAt, p.id
        """,
        countQuery = """
        SELECT COUNT(p) FROM EventParticipation p
         WHERE p.event.id = :eventId
           AND p.status = com.musicclubapp.entity.ParticipationStatus.GOING
           AND (p.hidden = false OR p.user.username = :viewer)
           AND p.user.id NOT IN :hiddenIds
        """)
    Page<EventParticipation> attendees(@Param("eventId") Long eventId,
                                       @Param("viewer") String viewer,
                                       @Param("friendIds") Collection<Long> friendIds,
                                       @Param("hiddenIds") Collection<Long> hiddenIds,
                                       Pageable pageable);

    /** Ile osob idzie, ale nie chce byc na liscie - pokazujemy sama liczbe. */
    long countByEventIdAndStatusAndHiddenTrue(Long eventId, ParticipationStatus status);

    /** Zakladka "Moje": nadchodzace wydarzenia, na ktore sie zapisalem. */
    @Query(value = """
        SELECT p FROM EventParticipation p JOIN FETCH p.event e
         WHERE p.user.username = :username AND e.startDate >= :today
         ORDER BY e.startDate, e.startTime NULLS LAST, e.id
        """,
        countQuery = """
        SELECT COUNT(p) FROM EventParticipation p
         WHERE p.user.username = :username AND p.event.startDate >= :today
        """)
    Page<EventParticipation> mineUpcoming(@Param("username") String username,
                                          @Param("today") LocalDate today,
                                          Pageable pageable);

    /** Ktore z tych wydarzen maja choc jeden zapis - tych import nie kasuje. */
    @Query("SELECT DISTINCT p.event.id FROM EventParticipation p WHERE p.event.id IN :eventIds")
    List<Long> eventsWithParticipants(@Param("eventIds") Collection<Long> eventIds);

    @Modifying
    @Query("DELETE FROM EventParticipation p WHERE p.event.id IN :eventIds")
    void deleteByEventIds(@Param("eventIds") Collection<Long> eventIds);

    @Modifying
    @Query("DELETE FROM EventParticipation p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Kandydaci do przypomnienia: zapisy na wydarzenia z najblizszych dni,
     * bez wycofanych i odwolanych, u osob, ktore chca przypomnien.
     */
    @Query("""
           SELECT p FROM EventParticipation p
           JOIN FETCH p.event e
           JOIN FETCH p.user u
           WHERE e.startDate BETWEEN :from AND :to
             AND e.withdrawnAt IS NULL
             AND e.status <> com.musicclubapp.entity.EventStatus.CANCELLED
             AND u.eventReminders = true
           """)
    List<EventParticipation> dueForReminder(@Param("from") java.time.LocalDate from,
                                            @Param("to") java.time.LocalDate to);

    /** Zapisy tej osoby razem z wydarzeniami - do pobrania wlasnych danych. */
    @Query("SELECT p FROM EventParticipation p JOIN FETCH p.event WHERE p.user.id = :userId ORDER BY p.createdAt")
    List<EventParticipation> ofUser(@Param("userId") Long userId);
}
