package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanInvitation;
import com.musicclubapp.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClanInvitationRepository extends JpaRepository<ClanInvitation, Long> {

    Optional<ClanInvitation> findByClanIdAndInviteeId(Long clanId, Long inviteeId);

    /** Zaproszenia czekajace na odpowiedz tej osoby, od najnowszych. */
    @Query("""
           SELECT i FROM ClanInvitation i JOIN FETCH i.clan JOIN FETCH i.inviter
           WHERE i.invitee.id = :userId AND i.status = com.musicclubapp.entity.InvitationStatus.PENDING
           ORDER BY i.createdAt DESC
           """)
    List<ClanInvitation> pendingFor(@Param("userId") Long userId);

    /** Kogo klan zaprosil i jeszcze nie ma odpowiedzi. */
    @Query("""
           SELECT i FROM ClanInvitation i JOIN FETCH i.invitee JOIN FETCH i.inviter
           WHERE i.clan.id = :clanId AND i.status = com.musicclubapp.entity.InvitationStatus.PENDING
           ORDER BY i.createdAt DESC
           """)
    List<ClanInvitation> pendingOf(@Param("clanId") Long clanId);

    long countByClanIdAndStatus(Long clanId, InvitationStatus status);

    @Query("SELECT i FROM ClanInvitation i JOIN FETCH i.clan JOIN FETCH i.invitee JOIN FETCH i.inviter WHERE i.id = :id")
    Optional<ClanInvitation> findFull(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ClanInvitation i WHERE i.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    /** Po dolaczeniu do klanu inne zaproszenia tej osoby (takze odrzucone) nie maja juz sensu. */
    @Modifying
    @Query("DELETE FROM ClanInvitation i WHERE i.invitee.id = :userId")
    void deleteByInviteeId(@Param("userId") Long userId);

    /** Przy usuwaniu konta: zaproszenia, ktore ta osoba dostala i ktore wyslala. */
    @Modifying
    @Query("DELETE FROM ClanInvitation i WHERE i.invitee.id = :userId OR i.inviter.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
