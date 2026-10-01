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

    /** Klany widoczne w przegladarce. */
    @Query("SELECT c FROM Clan c WHERE c.listed = true")
    java.util.List<Clan> listed();

    /** Gatunki podane przez klany z przegladarki - jednym zapytaniem, zeby nie ladowac ich po jednym. */
    @Query("SELECT c.id AS clanId, g AS genre FROM Clan c JOIN c.genres g WHERE c.listed = true")
    java.util.List<ClanGenreRow> listedGenres();

    /** Gatunki klanu - przed skasowaniem wiersza (kasowanie zapytaniem nie rusza tabeli kolekcji). */
    @Modifying
    @Query(value = "DELETE FROM clan_genres WHERE clan_id = :id", nativeQuery = true)
    void deleteGenres(@Param("id") Long id);
}
