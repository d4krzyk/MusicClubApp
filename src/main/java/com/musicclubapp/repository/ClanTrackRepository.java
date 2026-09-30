package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanTrack;
import com.musicclubapp.music.MusicProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClanTrackRepository extends JpaRepository<ClanTrack, Long> {

    /** Propozycje z jednego tygodnia, od najstarszej. */
    @Query("""
           SELECT t FROM ClanTrack t JOIN FETCH t.proposer
           WHERE t.clan.id = :clanId AND t.weekStart = :week
           ORDER BY t.createdAt, t.id
           """)
    List<ClanTrack> ofWeek(@Param("clanId") Long clanId, @Param("week") LocalDate week);

    /** Propozycje z tygodni w zadanym zakresie (do historii) - najnowsze tygodnie pierwsze. */
    @Query("""
           SELECT t FROM ClanTrack t JOIN FETCH t.proposer
           WHERE t.clan.id = :clanId AND t.weekStart >= :from AND t.weekStart < :before
           ORDER BY t.weekStart DESC, t.createdAt, t.id
           """)
    List<ClanTrack> ofWeeks(@Param("clanId") Long clanId, @Param("from") LocalDate from,
                            @Param("before") LocalDate before);

    long countByClanIdAndProposerIdAndWeekStart(Long clanId, Long proposerId, LocalDate weekStart);

    boolean existsByClanIdAndWeekStartAndMusicProviderAndMusicExternalId(
        Long clanId, LocalDate weekStart, MusicProvider provider, String externalId);

    /** Propozycje tej osoby - do pobrania wlasnych danych. */
    @Query("SELECT t FROM ClanTrack t JOIN FETCH t.clan WHERE t.proposer.id = :userId ORDER BY t.id")
    List<ClanTrack> ofUser(@Param("userId") Long userId);

    /** Glosy znikaja razem z propozycjami (ON DELETE CASCADE). */
    @Modifying
    @Query("DELETE FROM ClanTrack t WHERE t.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    @Modifying
    @Query("DELETE FROM ClanTrack t WHERE t.proposer.id = :userId")
    void deleteByProposerId(@Param("userId") Long userId);
}
