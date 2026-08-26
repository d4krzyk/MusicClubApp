package com.musicclubapp.repository;

import com.musicclubapp.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Dostep do katalogu artystow zapisanych w naszej bazie.
 *
 * <p>Wyszukiwanie po {@code externalId} jest tu najwazniejsze: zanim zapiszemy
 * nowego artyste, sprawdzamy, czy kogos takiego juz nie mamy. Bez tego kazde
 * polubienie tworzyloby nowy wiersz i porownywanie gustow przestaloby
 * cokolwiek znaczyc.</p>
 */
@Repository
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByExternalId(String externalId);
}
