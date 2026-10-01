package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClanTitleRepository extends JpaRepository<ClanTitle, Long> {

    @Query("SELECT t FROM ClanTitle t WHERE t.clan.id = :clanId ORDER BY t.id")
    List<ClanTitle> ofClan(@Param("clanId") Long clanId);

    long countByClanId(Long clanId);

    boolean existsByClanIdAndNameKey(Long clanId, String nameKey);

    boolean existsByClanIdAndNameKeyAndIdNot(Long clanId, String nameKey, Long id);

    @Modifying
    @Query("DELETE FROM ClanTitle t WHERE t.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);
}
