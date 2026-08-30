package com.musicclubapp.config;

import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.JDBCException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test bledu, ktorego zwykle testy nie mialy jak zlapac. */
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
         * Gdyby ten wyjatek nie polecial, test nie sprawdzalby niczego - upewniamy sie, ze
         * sytuacja z produkcji jest tu naprawde odtworzona.
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

    /** Pilnuje, ze lista kolumn w odswiezaczu nadaza za modelem encji. */
    @Test
    @DisplayName("kazda kolumna wyliczeniowa w modelu jest objeta odswiezaniem")
    void everyEnumColumnIsCovered() {
        Set<String> covered = EnumConstraintRefresher.coveredColumns();
        List<String> missing = new ArrayList<>();

        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            Class<?> type = entity.getJavaType();
            Table table = type.getAnnotation(Table.class);
            if (table == null || table.name().isBlank()) {
                continue;
            }

            for (Field field : type.getDeclaredFields()) {
                Enumerated enumerated = field.getAnnotation(Enumerated.class);
                if (enumerated == null || enumerated.value() != EnumType.STRING) {
                    continue;
                }

                String name = table.name() + "." + columnName(field);
                if (!covered.contains(name)) {
                    missing.add(name + "  (" + type.getSimpleName()
                        + "." + field.getName() + ")");
                }
            }
        }

        assertThat(missing)
            .describedAs("Kolumny wyliczeniowe bez odswiezania ograniczenia CHECK. "
                + "Dopisz je do EnumConstraintRefresher.COLUMNS, inaczej po dolozeniu "
                + "nowej wartosci do enuma kazdy zapis do tej kolumny skonczy sie "
                + "bledem 500 na bazie, ktora juz istniala.")
            .isEmpty();
    }

    /** Nazwa kolumny: z {@code @Column}, a bez niej - pole zapisane z podkresleniami. */
    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }
        return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
