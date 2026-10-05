package com.musicclubapp.repository;

import com.musicclubapp.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    /** Ile spotkan osoba ma jeszcze przed soba (nie odwolanych i nie zakonczonych) - limit przeciw zasypywaniu. */
    @Query("""
        SELECT COUNT(m) FROM Meeting m
        WHERE m.creator.id = :creatorId AND m.cancelledAt IS NULL AND m.endsAt > :now
        """)
    long countOpenBy(@Param("creatorId") Long creatorId, @Param("now") Instant now);

    /** Spotkania rozmowy dwoch osob zmienione po podanej chwili (odpowiedzi, odwolanie) - do odswiezania czatu. */
    @Query("""
        SELECT m FROM Meeting m
        WHERE m.clan IS NULL AND m.crew IS NULL AND m.updatedAt > :since
          AND ((m.creator.id = :a AND m.partner.id = :b) OR (m.creator.id = :b AND m.partner.id = :a))
        """)
    List<Meeting> changedInConversation(@Param("a") Long a, @Param("b") Long b, @Param("since") Instant since);

    /** To samo dla czatu klanu. */
    @Query("SELECT m FROM Meeting m WHERE m.clan.id = :clanId AND m.updatedAt > :since")
    List<Meeting> changedInClan(@Param("clanId") Long clanId, @Param("since") Instant since);

    /** I dla czatu ekipy. */
    @Query("SELECT m FROM Meeting m WHERE m.crew.id = :crewId AND m.updatedAt > :since")
    List<Meeting> changedInCrew(@Param("crewId") Long crewId, @Param("since") Instant since);

    /** Zalozone przez osobe - do eksportu danych. */
    List<Meeting> findByCreatorIdOrderByIdAsc(Long creatorId);

    /**
     * Usuniecie konta: spotkania zalozone przez osobe i spotkania z jej rozmow. Odpowiedzi i powiadomienia znikaja
     * kaskada w bazie, wiadomosci traca odnosnik ({@code ON DELETE SET NULL}).
     */
    @Modifying
    @Query("DELETE FROM Meeting m WHERE m.creator.id = :userId OR m.partner.id = :userId")
    int deleteAllOf(@Param("userId") Long userId);

    /**
     * Spotkania, ktorych nie niesie juz zadna wiadomosc (rozmowa skasowana przez obie strony) - nikt ich nie zobaczy,
     * wiec nie ma po co ich trzymac ani o nich przypominac. Kazdy czat ze spotkaniami musi tu byc - inaczej skasowanie
     * dowolnej rozmowy zabieraloby jego spotkania.
     */
    @Modifying
    @Query(value = """
        DELETE FROM meetings m
        WHERE NOT EXISTS (SELECT 1 FROM messages x WHERE x.meeting_id = m.id)
          AND NOT EXISTS (SELECT 1 FROM clan_messages c WHERE c.meeting_id = m.id)
          AND NOT EXISTS (SELECT 1 FROM crew_messages e WHERE e.meeting_id = m.id)
        """, nativeQuery = true)
    int deleteOrphans();
}
