package com.musicclubapp.repository;

import com.musicclubapp.entity.CrewMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {

    @Query("SELECT m FROM CrewMember m JOIN FETCH m.user WHERE m.crew.id IN :crewIds ORDER BY m.joinedAt, m.id")
    List<CrewMember> ofCrews(@Param("crewIds") Collection<Long> crewIds);

    Optional<CrewMember> findByCrewIdAndUserId(Long crewId, Long userId);

    /** W ktorej ekipie na to wydarzenie jestem (najwyzej jednej). */
    Optional<CrewMember> findByEventIdAndUserId(Long eventId, Long userId);

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    long countByCrewId(Long crewId);

    /** W ktorych z tych wydarzen mam ekipe - do kart na liscie wydarzen. */
    @Query("SELECT m.eventId AS eventId, m.crew.id AS crewId FROM CrewMember m WHERE m.user.username = :username AND m.eventId IN :ids")
    List<MyCrewRow> mineAmong(@Param("username") String username, @Param("ids") Collection<Long> ids);

    interface MyCrewRow {
        Long getEventId();

        Long getCrewId();
    }

    /** Moje ekipy na nadchodzace (niewycofane) koncerty, od najblizszego. */
    @Query("""
        SELECT m FROM CrewMember m JOIN FETCH m.crew c JOIN FETCH c.event e
         WHERE m.user.id = :userId AND e.startDate >= :today
         ORDER BY e.startDate, e.startTime, c.id
        """)
    List<CrewMember> upcomingOf(@Param("userId") Long userId, @Param("today") LocalDate today);

    /** Wszystkie moje czlonkostwa - do eksportu i usuniecia konta. */
    @Query("SELECT m FROM CrewMember m JOIN FETCH m.crew c JOIN FETCH c.event WHERE m.user.id = :userId ORDER BY m.id")
    List<CrewMember> ofUser(@Param("userId") Long userId);

    /** Komu wyslac push z czatu: czlonkowie bez zadnej nieprzeczytanej (jak w klanie), poza piszacym. */
    @Query("""
        SELECT m FROM CrewMember m JOIN FETCH m.user
         WHERE m.crew.id = :crewId AND m.user.id <> :senderId
           AND NOT EXISTS (SELECT 1 FROM CrewMessage x WHERE x.crew.id = :crewId AND x.id > COALESCE(m.chatReadId, 0)
                             AND x.id < :messageId AND x.sender.id <> m.user.id AND x.deletedAt IS NULL)
        """)
    List<CrewMember> toNotify(@Param("crewId") Long crewId, @Param("senderId") Long senderId,
                              @Param("messageId") Long messageId);

    @Modifying
    @Query("DELETE FROM CrewMember m WHERE m.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
