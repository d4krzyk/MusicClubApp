package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanPoll;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClanPollRepository extends JpaRepository<ClanPoll, Long> {

    /** Ankiety klanu od najnowszej (otwarte i zamkniete - rozroznia je serwis). */
    @Query("SELECT p FROM ClanPoll p JOIN FETCH p.author WHERE p.clan.id = :clanId ORDER BY p.id DESC")
    List<ClanPoll> latest(@Param("clanId") Long clanId, Pageable limit);

    @Query("""
           SELECT COUNT(p) FROM ClanPoll p
           WHERE p.clan.id = :clanId AND p.closed = false AND p.closesAt > :now
           """)
    long countOpen(@Param("clanId") Long clanId, @Param("now") LocalDateTime now);

    @Query("""
           SELECT COUNT(p) FROM ClanPoll p
           WHERE p.clan.id = :clanId AND p.author.id = :authorId AND p.closed = false AND p.closesAt > :now
           """)
    long countOpenOf(@Param("clanId") Long clanId, @Param("authorId") Long authorId, @Param("now") LocalDateTime now);

    /** Odpowiedzi i glosy znikaja razem z ankieta (ON DELETE CASCADE w bazie). */
    @Modifying
    @Query("DELETE FROM ClanPoll p WHERE p.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    @Modifying
    @Query("DELETE FROM ClanPoll p WHERE p.author.id = :userId")
    void deleteByAuthorId(@Param("userId") Long userId);

    /** Ankiety napisane przez te osobe - do pobrania wlasnych danych. */
    @Query("SELECT p FROM ClanPoll p JOIN FETCH p.clan WHERE p.author.id = :userId ORDER BY p.id")
    List<ClanPoll> writtenBy(@Param("userId") Long userId);
}
