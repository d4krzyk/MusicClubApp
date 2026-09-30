package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanTrackVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanTrackVoteRepository extends JpaRepository<ClanTrackVote, Long> {

    @Query("SELECT v FROM ClanTrackVote v WHERE v.track.id = :trackId AND v.user.id = :userId")
    Optional<ClanTrackVote> findMine(@Param("trackId") Long trackId, @Param("userId") Long userId);

    /** Liczniki glosow na propozycje - bez osob z blokad ogladajacego; "mine" to jego wlasny glos. */
    @Query("""
           SELECT v.track.id AS trackId, COUNT(v) AS total,
                  SUM(CASE WHEN v.user.id = :viewerId THEN 1 ELSE 0 END) AS mine
           FROM ClanTrackVote v
           WHERE v.track.id IN :trackIds AND v.user.id NOT IN :hidden
           GROUP BY v.track.id
           """)
    List<ClanVoteRow> counts(@Param("trackIds") Collection<Long> trackIds, @Param("viewerId") Long viewerId,
                             @Param("hidden") Collection<Long> hidden);

    /** Glosy tej osoby - do pobrania wlasnych danych. */
    @Query("SELECT v FROM ClanTrackVote v JOIN FETCH v.track t JOIN FETCH t.clan WHERE v.user.id = :userId ORDER BY v.id")
    List<ClanTrackVote> ofUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanTrackVote v WHERE v.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanTrackVote v WHERE v.track.id = :trackId")
    void deleteByTrackId(@Param("trackId") Long trackId);

    /** Glosy pod propozycjami tej osoby - przed ich skasowaniem. */
    @Modifying
    @Query("DELETE FROM ClanTrackVote v WHERE v.track.id IN (SELECT t.id FROM ClanTrack t WHERE t.proposer.id = :userId)")
    void deleteUnderTracksOf(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanTrackVote v WHERE v.track.id IN (SELECT t.id FROM ClanTrack t WHERE t.clan.id = :clanId)")
    void deleteByClanId(@Param("clanId") Long clanId);
}
