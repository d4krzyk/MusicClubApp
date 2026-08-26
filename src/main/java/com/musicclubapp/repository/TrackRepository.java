package com.musicclubapp.repository;

import com.musicclubapp.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Dostep do katalogu utworow - zasada ta sama co w {@link ArtistRepository}. */
@Repository
public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findByExternalId(String externalId);
}
