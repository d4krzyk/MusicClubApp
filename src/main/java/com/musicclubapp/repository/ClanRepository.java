package com.musicclubapp.repository;

import com.musicclubapp.entity.Clan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClanRepository extends JpaRepository<Clan, Long> {

    boolean existsByNameKeyAndIdNot(String nameKey, Long id);

    boolean existsByNameKey(String nameKey);

    boolean existsByTagAndIdNot(String tag, Long id);

    boolean existsByTag(String tag);

    /**
     * Kasuje wiersz klanu bez ladowania encji. Kasowanie przez em.remove() na encji odpietej
     * (po zapytaniach czyszczacych kontekst) scala ja z powrotem razem z lista czlonkow, ktorych
     * wiersze juz nie istnieja - i konczy sie "Unable to find ClanMember".
     */
    @Modifying
    @Query("DELETE FROM Clan c WHERE c.id = :id")
    void deleteRow(@Param("id") Long id);
}
