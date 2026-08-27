package com.musicclubapp.config;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uzupelnia widocznosc postom, ktore powstaly, zanim to pole w ogole istnialo.
 *
 * <p><b>Skad problem.</b> Do encji {@code Post} doszla kolumna
 * {@code visibility}. Hibernate w trybie {@code ddl-auto=update} doklada nowa
 * kolumne do istniejacej tabeli, ale <b>nie wpisuje do niej niczego</b> -
 * wszystkie dotychczasowe posty maja tam pustke. Bez tego kroku wpis sprzed
 * zmiany nie byloby ani publiczny, ani prywatny: zapytanie
 * {@code visibility = 'PUBLIC'} po prostu by go nie zlapalo i cala dotychczasowa
 * tablica zniknelaby uzytkownikom z oczu.</p>
 *
 * <p><b>Dlaczego wszystkie stare posty ida jako publiczne.</b> Bo takie byly
 * w chwili pisania - w aplikacji nie istnialo wtedy pojecie posta dla
 * znajomych. Ustawienie ich na "tylko znajomi" ukryloby tresci, ktore ich
 * autorzy swiadomie opublikowali dla wszystkich; to zmiana ich decyzji,
 * a nie jej odtworzenie.</p>
 *
 * <p><b>Da sie uruchomic wielokrotnie</b> - warunek {@code IS NULL} sprawia,
 * ze drugi start nie rusza juz uzupelnionych wierszy. To wazne, bo aplikacja
 * startuje wiele razy, a przepisanie ma zadzialac dokladnie raz. Ta sama
 * zasada co w {@link MusicLinkMigration}.</p>
 */
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
             * Brak kolumny oznacza baze starsza niz ta zmiana, do ktorej
             * Hibernate jeszcze nie zdazyl dolozyc kolumny. Zatrzymywanie
             * z tego powodu calej aplikacji byloby gorsze niz wpis w logu.
             */
            log.warn("Nie udalo sie uzupelnic widocznosci postow - aplikacja startuje dalej: {}",
                e.getMessage());
        }
    }
}
