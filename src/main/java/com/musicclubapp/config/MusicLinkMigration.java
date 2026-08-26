package com.musicclubapp.config;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Przepisuje posty sprzed rozdzielenia muzyki na serwisy.
 *
 * <p><b>Skad w ogole problem.</b> Wczesniej post trzymal {@code spotify_track_id}
 * i {@code spotify_start_seconds}. Teraz sa to {@code music_provider},
 * {@code music_kind}, {@code music_external_id} i {@code music_start_seconds}.
 * Bez przepisania stare posty straciłyby odtwarzacze - dane leżałyby w bazie,
 * ale w kolumnach, do których nikt już nie zagląda.</p>
 *
 * <p><b>Dlaczego to jest tutaj, a nie w narzedziu do migracji?</b> Bo projekt
 * uzywa {@code ddl-auto=update} - Hibernate sam dokłada nowe kolumny, ale nie
 * przenosi danych. Przy większym projekcie byłby tu Flyway albo Liquibase;
 * przy jednej jednorazowej zmianie osobne narzędzie i cały jego bagaż
 * byłyby cięższe niż to, co ma załatwić.</p>
 *
 * <p><b>Da sie uruchomic wielokrotnie bez szkody.</b> Warunek
 * {@code music_external_id IS NULL} sprawia, że drugi start nie rusza już
 * przepisanych postów. To ważne: aplikacja startuje wiele razy, a migracja
 * ma zadziałać dokładnie raz.</p>
 *
 * <p><b>Starej kolumny NIE kasujemy automatycznie.</b> Gdyby coś poszło nie
 * tak, dane są nadal na miejscu i da się je odzyskać. Po sprawdzeniu, że
 * wszystko gra, można ją usunąć ręcznie:
 * {@code ALTER TABLE posts DROP COLUMN spotify_track_id, DROP COLUMN spotify_start_seconds;}</p>
 */
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

    /**
     * Sprawdza, czy stara kolumna w ogóle istnieje.
     *
     * <p>Na świeżo założonej bazie jej nie ma, a {@code UPDATE} odwołujący się
     * do nieistniejącej kolumny wywaliłby start aplikacji. Pytamy więc
     * katalog systemowy zamiast zakładać.</p>
     */
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
