package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanMessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanMessageReactionRepository extends JpaRepository<ClanMessageReaction, Long> {

    @Query("SELECT r FROM ClanMessageReaction r WHERE r.message.id = :messageId AND r.user.id = :userId")
    Optional<ClanMessageReaction> findMine(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /**
     * Liczniki reakcji na wiadomosci klanu od podanej wzwyz, bez osob z blokad ogladajacego.
     * "mine" - ile z nich (0 albo 1) to reakcja ogladajacego.
     */
    @Query("""
           SELECT r.message.id AS messageId, r.type AS type, COUNT(r) AS total,
                  SUM(CASE WHEN r.user.id = :viewerId THEN 1 ELSE 0 END) AS mine
           FROM ClanMessageReaction r
           WHERE r.message.clan.id = :clanId AND r.message.id >= :since AND r.user.id NOT IN :hidden
           GROUP BY r.message.id, r.type
           ORDER BY r.message.id
           """)
    List<ClanReactionRow> summaries(@Param("clanId") Long clanId, @Param("since") Long since,
                                    @Param("viewerId") Long viewerId, @Param("hidden") Collection<Long> hidden);

    /** To samo dla jednej wiadomosci. */
    @Query("""
           SELECT r.message.id AS messageId, r.type AS type, COUNT(r) AS total,
                  SUM(CASE WHEN r.user.id = :viewerId THEN 1 ELSE 0 END) AS mine
           FROM ClanMessageReaction r
           WHERE r.message.id = :messageId AND r.user.id NOT IN :hidden
           GROUP BY r.message.id, r.type
           """)
    List<ClanReactionRow> summaryOf(@Param("messageId") Long messageId, @Param("viewerId") Long viewerId,
                                    @Param("hidden") Collection<Long> hidden);

    @Modifying
    @Query("DELETE FROM ClanMessageReaction r WHERE r.message.id = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);

    /** Reakcje tej osoby - do pobrania wlasnych danych. */
    @Query("SELECT r FROM ClanMessageReaction r JOIN FETCH r.message m JOIN FETCH m.clan WHERE r.user.id = :userId ORDER BY r.id")
    List<ClanMessageReaction> ofUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanMessageReaction r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** Wszystkie reakcje pod wiadomosciami klanu - przed rozwiazaniem klanu (baza kasuje je tez kaskadowo). */
    @Modifying
    @Query("DELETE FROM ClanMessageReaction r WHERE r.message.id IN (SELECT m.id FROM ClanMessage m WHERE m.clan.id = :clanId)")
    void deleteByClanId(@Param("clanId") Long clanId);

    /** Reakcje pod wiadomosciami tej osoby - przed skasowaniem jej wiadomosci. */
    @Modifying
    @Query("DELETE FROM ClanMessageReaction r WHERE r.message.id IN (SELECT m.id FROM ClanMessage m WHERE m.sender.id = :userId)")
    void deleteUnderMessagesOf(@Param("userId") Long userId);
}
