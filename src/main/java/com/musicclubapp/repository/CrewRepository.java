package com.musicclubapp.repository;

import com.musicclubapp.entity.Crew;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CrewRepository extends JpaRepository<Crew, Long> {

    @Query("SELECT c FROM Crew c JOIN FETCH c.founder WHERE c.event.id = :eventId ORDER BY c.id")
    List<Crew> ofEvent(@Param("eventId") Long eventId);

    /** Ile ekip ma kazde z wydarzen - do kart na liscie wydarzen. */
    @Query("SELECT c.event.id AS eventId, COUNT(c) AS total FROM Crew c WHERE c.event.id IN :ids GROUP BY c.event.id")
    List<EventCountRow> countByEvents(@Param("ids") java.util.Collection<Long> ids);

    /** Ile ekip na nadchodzace koncerty prowadzi ta osoba - limit przeciw zasypywaniu. */
    @Query("SELECT COUNT(c) FROM Crew c WHERE c.founder.id = :userId AND c.event.startDate >= :today")
    long countFoundedUpcoming(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * Rozwiazanie ekipy - zapytaniem, nie {@code remove}: reszte kasuje baza (ON DELETE CASCADE). Bez czyszczenia
     * kontekstu - usuniecie konta dziala dalej na encji konta, ktora by sie odpiela.
     */
    @Modifying
    @Query("DELETE FROM Crew c WHERE c.id = :id")
    void deleteRow(@Param("id") Long id);

    interface EventCountRow {
        Long getEventId();

        long getTotal();
    }
}
