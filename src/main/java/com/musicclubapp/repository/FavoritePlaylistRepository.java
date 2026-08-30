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

    /** Czy ta osoba ma juz te playliste. */
    Optional<FavoritePlaylist> findByOwnerUsernameAndProviderAndExternalId(
        String username, MusicProvider provider, String externalId);

    /** Kasuje gablotke razem z kontem. */
    @Modifying
    @Query("DELETE FROM FavoritePlaylist p WHERE p.owner.id = :userId")
    void deleteByOwnerId(@Param("userId") Long userId);
}
