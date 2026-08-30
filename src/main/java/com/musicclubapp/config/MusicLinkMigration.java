package com.musicclubapp.config;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Przepisuje posty sprzed rozdzielenia muzyki na serwisy. */
@Component
public class MusicLinkMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MusicLinkMigration.class);

    private final EntityManager entityManager;

    public MusicLinkMigration(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!oldColumnExists()) {
            // Swieza baza - nie ma czego przepisywac
            return;
        }

        int rewritten = entityManager.createNativeQuery("""
            UPDATE posts
               SET music_provider      = 'SPOTIFY',
                   music_kind          = 'TRACK',
                   music_external_id   = spotify_track_id,
                   music_start_seconds = spotify_start_seconds
             WHERE spotify_track_id IS NOT NULL
               AND music_external_id IS NULL
            """).executeUpdate();

        if (rewritten > 0) {
            log.info("Przepisano {} postow ze starego pola spotify_track_id "
                + "na nowe kolumny music_*. Stara kolumna zostaje na wszelki wypadek.",
                rewritten);
        }
    }

    /** Sprawdza, czy stara kolumna w ogóle istnieje. */
    private boolean oldColumnExists() {
        try {
            Object score = entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_name = 'posts' AND column_name = 'spotify_track_id'
                """).getSingleResult();

            return ((Number) score).intValue() > 0;
        } catch (Exception e) {
            log.warn("Nie udalo sie sprawdzic starej kolumny - pomijam migracje: {}",
                e.getMessage());
            return false;
        }
    }
}
