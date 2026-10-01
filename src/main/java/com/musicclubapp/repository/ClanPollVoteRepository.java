package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanPollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanPollVoteRepository extends JpaRepository<ClanPollVote, Long> {

    @Query("SELECT v FROM ClanPollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    Optional<ClanPollVote> findMine(@Param("pollId") Long pollId, @Param("userId") Long userId);

    /** Liczniki glosow na odpowiedzi tych ankiet - bez osob z blokad ogladajacego. */
    @Query("""
           SELECT v.option.id AS optionId, COUNT(v) AS total,
                  SUM(CASE WHEN v.user.id = :viewerId THEN 1 ELSE 0 END) AS mine
           FROM ClanPollVote v
           WHERE v.poll.id IN :pollIds AND v.user.id NOT IN :hidden
           GROUP BY v.option.id
           """)
    List<ClanPollVoteRow> counts(@Param("pollIds") Collection<Long> pollIds, @Param("viewerId") Long viewerId,
                                 @Param("hidden") Collection<Long> hidden);

    /** Glosy tej osoby - do pobrania wlasnych danych. */
    @Query("SELECT v FROM ClanPollVote v JOIN FETCH v.poll p JOIN FETCH p.clan JOIN FETCH v.option WHERE v.user.id = :userId ORDER BY v.id")
    List<ClanPollVote> ofUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanPollVote v WHERE v.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** Glosy pod ankietami tej osoby - przed ich skasowaniem. */
    @Modifying
    @Query("DELETE FROM ClanPollVote v WHERE v.poll.id IN (SELECT p.id FROM ClanPoll p WHERE p.author.id = :userId)")
    void deleteUnderPollsOf(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanPollVote v WHERE v.poll.id = :pollId")
    void deleteByPollId(@Param("pollId") Long pollId);

    @Modifying
    @Query("DELETE FROM ClanPollVote v WHERE v.poll.id IN (SELECT p.id FROM ClanPoll p WHERE p.clan.id = :clanId)")
    void deleteByClanId(@Param("clanId") Long clanId);
}
