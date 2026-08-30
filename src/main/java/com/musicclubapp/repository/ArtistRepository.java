package com.musicclubapp.repository;

import com.musicclubapp.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Dostep do katalogu artystow zapisanych w naszej bazie. */
@Repository
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByExternalId(String externalId);
}
