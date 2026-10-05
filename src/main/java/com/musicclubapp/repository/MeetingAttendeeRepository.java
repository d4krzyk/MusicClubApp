package com.musicclubapp.repository;

import com.musicclubapp.entity.MeetingAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, Long> {

    Optional<MeetingAttendee> findByMeetingIdAndUserId(Long meetingId, Long userId);

    /** Odpowiedzi pod kilkoma spotkaniami naraz (strona czatu) - od najwczesniejszej. */
    @Query("""
        SELECT a FROM MeetingAttendee a JOIN FETCH a.user
        WHERE a.meeting.id IN :ids
        ORDER BY a.id
        """)
    List<MeetingAttendee> forMeetings(@Param("ids") Collection<Long> ids);

    /** Potwierdzeni - do powiadomienia o odwolaniu. */
    @Query("""
        SELECT a FROM MeetingAttendee a JOIN FETCH a.user
        WHERE a.meeting.id = :meetingId AND a.status = com.musicclubapp.entity.MeetingStatus.GOING
        """)
    List<MeetingAttendee> going(@Param("meetingId") Long meetingId);

    /**
     * Komu juz pora przypomniec: potwierdzil, nie dostal, chwila przypomnienia minela, a spotkanie nie jest
     * odwolane ani zakonczone (serwer, ktory stal, nie przypomina o spotkaniu, ktore sie odbylo).
     */
    @Query("""
        SELECT a FROM MeetingAttendee a JOIN FETCH a.meeting m JOIN FETCH a.user
        WHERE a.status = com.musicclubapp.entity.MeetingStatus.GOING AND a.remindedAt IS NULL
          AND m.remindAt <= :now AND m.cancelledAt IS NULL AND m.endsAt > :now
        ORDER BY m.remindAt, a.id
        """)
    List<MeetingAttendee> dueForReminder(@Param("now") Instant now);

    /** Moje odpowiedzi - do eksportu danych. */
    @Query("SELECT a FROM MeetingAttendee a JOIN FETCH a.meeting WHERE a.user.id = :userId ORDER BY a.id")
    List<MeetingAttendee> ofUser(@Param("userId") Long userId);

    /** Odejscie z klanu: odpowiedzi na spotkania tego klanu znikaja (przypomnienia tez nie przyjda). */
    @Modifying
    @Query("""
        DELETE FROM MeetingAttendee a
        WHERE a.user.id = :userId AND a.meeting.id IN (SELECT m.id FROM Meeting m WHERE m.clan.id = :clanId)
        """)
    int deleteInClan(@Param("userId") Long userId, @Param("clanId") Long clanId);

    /** Odejscie z ekipy: odpowiedzi na spotkania tej ekipy znikaja. */
    @Modifying
    @Query("""
        DELETE FROM MeetingAttendee a
        WHERE a.user.id = :userId AND a.meeting.id IN (SELECT m.id FROM Meeting m WHERE m.crew.id = :crewId)
        """)
    int deleteInCrew(@Param("userId") Long userId, @Param("crewId") Long crewId);
}
