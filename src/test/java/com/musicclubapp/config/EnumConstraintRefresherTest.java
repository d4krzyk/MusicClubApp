package com.musicclubapp.config;

import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.JDBCException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test bledu, ktorego zwykle testy nie mialy jak zlapac.
 *
 * <p><b>Na czym polegal.</b> Do {@link MusicKind} doszła wartość
 * {@code PLAYLIST}. Wszystkie testy przechodziły, a na prawdziwej bazie
 * <b>każda</b> próba wrzucenia playlisty kończyła się błędem 500: baza miała
 * ograniczenie {@code CHECK} z listą wartości sprzed zmiany, a
 * {@code ddl-auto=update} takiego ograniczenia nigdy nie rusza.</p>
 *
 * <p><b>Dlaczego testy tego nie widziały.</b> H2 zakłada schemat od zera przy
 * każdym uruchomieniu, czyli od razu z pełną listą wartości. Błąd wymaga
 * bazy, która <i>istniała przed zmianą</i> — a takiej w testach nie ma.</p>
 *
 * <p><b>Sztuczka użyta tutaj.</b> Zanim cokolwiek sprawdzimy, ręcznie
 * <b>cofamy</b> ograniczenie do starej postaci. Dopiero na tak przygotowanej
 * bazie test ma sens — i wtedy rzeczywiście czerwienieje bez poprawki.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Ograniczenia CHECK nadazaja za wyliczeniami")
class EnumConstraintRefresherTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    /** Lista wartosci sprzed dolozenia PLAYLIST - taka siedzi w starej bazie. */
    private void revertConstraintToOldForm() {
        entityManager.createNativeQuery(
            "ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_music_kind_check").executeUpdate();
        entityManager.createNativeQuery(
            "ALTER TABLE posts ADD CONSTRAINT posts_music_kind_check "
                + "CHECK (music_kind IN ('TRACK', 'ALBUM', 'ARTIST'))").executeUpdate();
    }

    /** Wstawia post z pominieciem Hibernate - interesuje nas sama reakcja bazy. */
    private void insertPost(String kind) {
        Long author = userRepository.save(
            new User("autor" + kind, kind + "@example.com", "hash")).getId();

        entityManager.createNativeQuery("""
            INSERT INTO posts (content, created_at, author_id, music_provider,
                               music_kind, music_external_id)
            VALUES ('tresc', CURRENT_TIMESTAMP, ?, 'SPOTIFY', ?, 'jakies-id')
            """)
            .setParameter(1, author)
            .setParameter(2, kind)
            .executeUpdate();
    }

    @Test
    @DisplayName("stara baza faktycznie odrzuca PLAYLIST - tak wygladal blad 500")
    void oldSchemaRejectsNewValue() {
        revertConstraintToOldForm();

        /*
         * Gdyby ten wyjatek nie polecial, test nie sprawdzalby niczego -
         * upewniamy sie, ze sytuacja z produkcji jest tu naprawde odtworzona.
         *
         * Sprawdzamy sam TYP wyjatku, a nie tresc komunikatu: H2 pisze
         * "Check constraint violation: POSTS_MUSIC_KIND_CHECK", a PostgreSQL
         * "violates check constraint posts_music_kind_check". Test przypiety
         * do jednego z tych zdan pekalby po zmianie bazy, choc kod bylby dobry.
         */
        assertThatThrownBy(() -> insertPost("PLAYLIST"))
            .isInstanceOf(JDBCException.class);
    }

    @Test
    @DisplayName("po odswiezeniu ta sama baza przyjmuje PLAYLIST")
    void afterRefreshNewValuePasses() {
        revertConstraintToOldForm();

        new EnumConstraintRefresher(entityManager).run(null);

        insertPost("PLAYLIST");

        Number count = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM posts WHERE music_kind = 'PLAYLIST'").getSingleResult();
        assertThat(count.intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("bledne wartosci nadal sa odrzucane - ograniczenie nie znika")
    void constraintStillGuardsValues() {
        revertConstraintToOldForm();

        new EnumConstraintRefresher(entityManager).run(null);

        // Odswiezenie ma liste POSZERZYC, a nie skasowac ochrone - inaczej
        // najprostsza "naprawa" byloby usuniecie ograniczenia i tyle
        assertThatThrownBy(() -> insertPost("PODCAST"))
            .isInstanceOf(JDBCException.class)
            .hasMessageContaining("PODCAST");
    }
}
