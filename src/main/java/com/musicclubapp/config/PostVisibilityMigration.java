package com.musicclubapp.config;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Uzupelnia widocznosc postom, ktore powstaly, zanim to pole w ogole istnialo. */
@Component
public class PostVisibilityMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostVisibilityMigration.class);

    private final EntityManager entityManager;

    public PostVisibilityMigration(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            int filled = entityManager.createNativeQuery("""
                UPDATE posts SET visibility = 'PUBLIC' WHERE visibility IS NULL
                """).executeUpdate();

            if (filled > 0) {
                log.info("Uzupelniono widocznosc {} postom sprzed wprowadzenia "
                    + "postow tylko dla znajomych - wszystkie zostaly publiczne.", filled);
            }
        } catch (Exception e) {
            /*
             * Brak kolumny oznacza baze starsza niz ta zmiana, do ktorej Hibernate jeszcze nie
             * zdazyl dolozyc kolumny.
             */
            log.warn("Nie udalo sie uzupelnic widocznosci postow - aplikacja startuje dalej: {}",
                e.getMessage());
        }
    }
}
