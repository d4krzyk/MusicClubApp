package com.musicclubapp.repository;

import com.musicclubapp.entity.ArtistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {

    Optional<ArtistProfile> findByNameKeyAndLang(String nameKey, String lang);

    /** Czy ktos o tej nazwie gra na jakimkolwiek wydarzeniu - opis pokazujemy tylko wykonawcom z koncertow. */
    @Query(value = "SELECT COUNT(*) > 0 FROM music_event_performers WHERE lower(name) = lower(:name)", nativeQuery = true)
    boolean isPerformer(@Param("name") String name);
}
