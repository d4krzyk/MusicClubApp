package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanMemberTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClanMemberTitleRepository extends JpaRepository<ClanMemberTitle, Long> {

    /** Tytuly nadane i wziete w calym klanie, razem z tytulami i osobami. */
    @Query("""
           SELECT mt FROM ClanMemberTitle mt JOIN FETCH mt.title t JOIN FETCH mt.user
           WHERE t.clan.id = :clanId ORDER BY mt.createdAt, mt.id
           """)
    List<ClanMemberTitle> ofClan(@Param("clanId") Long clanId);

    @Query("SELECT mt FROM ClanMemberTitle mt WHERE mt.title.id = :titleId AND mt.user.id = :userId")
    Optional<ClanMemberTitle> find(@Param("titleId") Long titleId, @Param("userId") Long userId);

    @Query("SELECT COUNT(mt) FROM ClanMemberTitle mt WHERE mt.title.clan.id = :clanId AND mt.user.id = :userId")
    long countOf(@Param("clanId") Long clanId, @Param("userId") Long userId);

    @Query("""
           SELECT COUNT(mt) FROM ClanMemberTitle mt
           WHERE mt.title.clan.id = :clanId AND mt.user.id = :userId AND mt.selfClaimed = true
           """)
    long countSelfClaimed(@Param("clanId") Long clanId, @Param("userId") Long userId);

    /** Tytuly tej osoby - do pobrania wlasnych danych. */
    @Query("SELECT mt FROM ClanMemberTitle mt JOIN FETCH mt.title t JOIN FETCH t.clan WHERE mt.user.id = :userId ORDER BY mt.id")
    List<ClanMemberTitle> ofUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ClanMemberTitle mt WHERE mt.title.id = :titleId")
    void deleteByTitleId(@Param("titleId") Long titleId);

    @Modifying
    @Query("DELETE FROM ClanMemberTitle mt WHERE mt.title.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    /** Odejscie z klanu: tytuly z TEGO klanu przestaja miec wlasciciela. */
    @Modifying
    @Query("DELETE FROM ClanMemberTitle mt WHERE mt.user.id = :userId AND mt.title.clan.id = :clanId")
    void deleteByUserIdAndClanId(@Param("userId") Long userId, @Param("clanId") Long clanId);

    @Modifying
    @Query("DELETE FROM ClanMemberTitle mt WHERE mt.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
