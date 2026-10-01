package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanJoinRequest;
import com.musicclubapp.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanJoinRequestRepository extends JpaRepository<ClanJoinRequest, Long> {

    Optional<ClanJoinRequest> findByClanIdAndUserId(Long clanId, Long userId);

    @Query("SELECT r FROM ClanJoinRequest r JOIN FETCH r.clan JOIN FETCH r.user WHERE r.id = :id")
    Optional<ClanJoinRequest> findFull(@Param("id") Long id);

    /** Prosby czekajace na decyzje zarzadu, od najnowszych. */
    @Query("""
           SELECT r FROM ClanJoinRequest r JOIN FETCH r.user
           WHERE r.clan.id = :clanId AND r.status = com.musicclubapp.entity.InvitationStatus.PENDING
           ORDER BY r.createdAt DESC
           """)
    List<ClanJoinRequest> pendingOf(@Param("clanId") Long clanId);

    /** Wszystkie prosby tej osoby (takze odrzucone) razem z klanami - od najnowszych. */
    @Query("SELECT r FROM ClanJoinRequest r JOIN FETCH r.clan WHERE r.user.id = :userId ORDER BY r.createdAt DESC")
    List<ClanJoinRequest> ofUser(@Param("userId") Long userId);

    long countByUserIdAndStatus(Long userId, InvitationStatus status);

    /** Stare, nieodebrane prosby i odrzucone, ktorych tydzien karencji minal - do posprzatania. */
    @Query("""
           SELECT r FROM ClanJoinRequest r JOIN FETCH r.clan JOIN FETCH r.user
           WHERE (r.status = com.musicclubapp.entity.InvitationStatus.PENDING AND r.createdAt < :pendingBefore)
              OR (r.status = com.musicclubapp.entity.InvitationStatus.DECLINED AND r.answeredAt < :declinedBefore)
           """)
    List<ClanJoinRequest> expired(@Param("pendingBefore") LocalDateTime pendingBefore,
                                  @Param("declinedBefore") LocalDateTime declinedBefore);

    @Modifying
    @Query("DELETE FROM ClanJoinRequest r WHERE r.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    /** Po dolaczeniu do klanu (albo przy usuwaniu konta) prosby tej osoby nie maja juz sensu. */
    @Modifying
    @Query("DELETE FROM ClanJoinRequest r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
