package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanMemberRepository extends JpaRepository<ClanMember, Long> {

    /** Klan tej osoby, jesli jakis ma. */
    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.id = :userId")
    Optional<ClanMember> findByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.username = :username")
    Optional<ClanMember> findByUsername(@Param("username") String username);

    /** Czlonkowie klanu razem z kontami: zalozyciel, administratorzy, potem reszta wedlug stazu. */
    @Query("""
           SELECT m FROM ClanMember m JOIN FETCH m.user
           WHERE m.clan.id = :clanId
           ORDER BY CASE m.role WHEN com.musicclubapp.entity.ClanRole.FOUNDER THEN 0
                                WHEN com.musicclubapp.entity.ClanRole.ADMIN THEN 1 ELSE 2 END,
                    m.joinedAt, m.id
           """)
    List<ClanMember> ofClan(@Param("clanId") Long clanId);

    long countByClanId(Long clanId);

    /** Czlonkostwa tych osob razem z klanami - do plakietek pod postami, jednym zapytaniem. */
    @Query("SELECT m FROM ClanMember m JOIN FETCH m.clan WHERE m.user.id IN :userIds")
    List<ClanMember> ofUsers(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM ClanMember m WHERE m.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);
}
