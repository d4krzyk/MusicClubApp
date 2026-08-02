package com.musicclubapp.repository;

import com.musicclubapp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repozytorium = warstwa dostepu do bazy.
 *
 * <p>Nie piszemy tu zadnej implementacji - Spring Data JPA tworzy ja sam w czasie
 * startu aplikacji. Wystarczy, ze rozszerzymy {@link JpaRepository}, a dostajemy
 * gotowe: {@code save()}, {@code findById()}, {@code findAll(Pageable)},
 * {@code delete()} i kilkadziesiat innych metod.</p>
 *
 * <p>{@code JpaRepository<User, Long>} czytamy jako: "repozytorium encji User,
 * ktorej klucz glowny jest typu Long".</p>
 *
 * <p><b>Realizuje wymagania z listy:</b></p>
 * <ul>
 *   <li>nr 3 i 5 - stronicowanie i sortowanie po stronie backendu przez {@link Pageable},</li>
 *   <li>nr 8 - wlasne zapytanie z adnotacja {@code @Query}.</li>
 * </ul>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Metoda pochodna (derived query) - Spring Data czyta NAZWE metody i sam
     * buduje z niej SQL. "findByUsername" -&gt; {@code WHERE username = ?}.
     *
     * <p>{@link Optional} zamiast zwyklego {@code User} wymusza na nas obsluzenie
     * przypadku "nie ma takiego uzytkownika" - stad pozniej wyjatek 404
     * (wymaganie nr 11).</p>
     */
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /** Przydaje sie przy rejestracji: "czy ten login jest juz zajety?" */
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Wlasne zapytanie JPQL - wymaganie nr 8.
     *
     * <p>Uwaga: JPQL operuje na ENCJACH i ich polach ({@code User u}, {@code u.username}),
     * a nie na tabelach i kolumnach SQL. {@code LOWER(...) LIKE LOWER(...)} daje
     * wyszukiwanie nieczule na wielkosc liter.</p>
     *
     * <p>Parametr {@link Pageable} sprawia, ze wynik jest stronicowany i sortowany
     * po stronie bazy - wymagania nr 3 i 5. Kontroler bedzie przyjmowal
     * {@code ?page=0&size=20&sort=createdAt,desc}.</p>
     *
     * @param fragment fragment loginu lub adresu e-mail wpisany w wyszukiwarke
     */
    @Query("""
           SELECT u FROM User u
           WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :fragment, '%'))
              OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :fragment, '%'))
           """)
    Page<User> searchByUsernameOrEmail(@Param("fragment") String fragment, Pageable pageable);
}
