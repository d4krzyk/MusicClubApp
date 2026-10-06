package com.musicclubapp.repository;

import com.musicclubapp.entity.Artist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Dostep do katalogu artystow zapisanych w naszej bazie. */
@Repository
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByExternalId(String externalId);

    /**
     * Ulubieni artysci (czyjs ulubiony - obcy katalog nas nie obchodzi) bez
     * gatunkow, ktorych jeszcze nie sprawdzilismy albo sprawdzilismy dawno.
     */
    @Query("""
        SELECT a FROM Artist a
         WHERE a.genres IS EMPTY
           AND (a.genresCheckedAt IS NULL OR a.genresCheckedAt < :checkedBefore)
           AND EXISTS (SELECT u FROM User u JOIN u.favoriteArtists f WHERE f.id = a.id)
         ORDER BY a.id
        """)
    List<Artist> needingGenres(@Param("checkedBefore") LocalDateTime checkedBefore, Pageable limit);

    /** Wykonawcy, ktorych lubi najwiecej osob - o ich koncerty pytamy zrodla bez wyszukiwania po miejscu. */
    @Query(value = """
        SELECT a.name FROM artists a JOIN user_favorite_artists f ON f.artist_id = a.id
         GROUP BY a.id, a.name ORDER BY COUNT(*) DESC, a.id
        """, nativeQuery = true)
    List<String> mostLiked(org.springframework.data.domain.Pageable limit);
}
