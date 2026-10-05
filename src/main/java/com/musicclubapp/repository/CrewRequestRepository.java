package com.musicclubapp.repository;

import com.musicclubapp.entity.CrewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrewRequestRepository extends JpaRepository<CrewRequest, Long> {

    Optional<CrewRequest> findByCrewIdAndUserId(Long crewId, Long userId);

    /** Moje prosby do ekip tego wydarzenia - do stanu "czeka" / "odmowa" na liscie. */
    @Query("SELECT r FROM CrewRequest r WHERE r.user.id = :userId AND r.crew.id IN :crewIds")
    List<CrewRequest> mine(@Param("userId") Long userId, @Param("crewIds") Collection<Long> crewIds);

    /** Czekajace prosby do ekipy - dla zakladajacego. */
    @Query("""
        SELECT r FROM CrewRequest r JOIN FETCH r.user
         WHERE r.crew.id = :crewId AND r.declinedAt IS NULL ORDER BY r.createdAt
        """)
    List<CrewRequest> pending(@Param("crewId") Long crewId);

    /** Wejscie do ekipy na wydarzenie konczy inne moje prosby na to samo wydarzenie. */
    @Modifying
    @Query("""
        DELETE FROM CrewRequest r
         WHERE r.user.id = :userId AND r.crew.id IN (SELECT c.id FROM Crew c WHERE c.event.id = :eventId)
        """)
    int deleteForEvent(@Param("userId") Long userId, @Param("eventId") Long eventId);

    @Query("SELECT r FROM CrewRequest r JOIN FETCH r.crew c JOIN FETCH c.event WHERE r.user.id = :userId ORDER BY r.id")
    List<CrewRequest> ofUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM CrewRequest r WHERE r.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Do sprzatania: odrzucone, ktorych tydzien karencji minal, i czekajace na koncert, ktory juz sie odbyl (nikt ich
     * nie przyjmie - na minione wydarzenie nie da sie dolaczyc).
     */
    @Query("""
        SELECT r FROM CrewRequest r JOIN FETCH r.crew c JOIN FETCH r.user
         WHERE (r.declinedAt IS NOT NULL AND r.declinedAt < :declinedBefore)
            OR (r.declinedAt IS NULL AND c.event.startDate < :today)
        """)
    List<CrewRequest> expired(@Param("declinedBefore") LocalDateTime declinedBefore, @Param("today") java.time.LocalDate today);
}
