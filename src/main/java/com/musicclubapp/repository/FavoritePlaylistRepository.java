package com.musicclubapp.repository;

import com.musicclubapp.entity.FavoritePlaylist;
import com.musicclubapp.music.MusicProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Gablotka playlist na profilu - patrz {@link FavoritePlaylist}. */
@Repository
public interface FavoritePlaylistRepository extends JpaRepository<FavoritePlaylist, Long> {

    /** Playlisty danej osoby, w kolejnosci ustawionej w gablotce. */
    List<FavoritePlaylist> findByOwnerUsernameOrderByPositionAsc(String username);

    long countByOwnerUsername(String username);

    /**
     * Czy ta osoba ma juz te playliste.
     *
     * <p>Osobne sprawdzenie, mimo ze pilnuje tego takze ograniczenie
     * {@code UNIQUE} w bazie. Ograniczenie chroni dane, ale odzywa sie
     * wyjatkiem bazodanowym, z ktorego uzytkownik nic nie wyczyta - to
     * sprawdzenie pozwala odpowiedziec mu po ludzku.</p>
     */
    Optional<FavoritePlaylist> findByOwnerUsernameAndProviderAndExternalId(
        String username, MusicProvider provider, String externalId);

    /**
     * Kasuje gablotke razem z kontem.
     *
     * <p>Jednym {@code DELETE}, bez pobierania encji: nie ma tu plikow na
     * dysku ani niczego, co trzeba by sprzatnac po drodze - w przeciwienstwie
     * do postow ze zdjeciami.</p>
     */
    @Modifying
    @Query("DELETE FROM FavoritePlaylist p WHERE p.owner.id = :userId")
    void deleteByOwnerId(@Param("userId") Long userId);
}
