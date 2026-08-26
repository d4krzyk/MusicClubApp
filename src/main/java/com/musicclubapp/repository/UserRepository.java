package com.musicclubapp.repository;

import com.musicclubapp.entity.Role;
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

    /** Czy istnieje juz ktos z podana rola - uzywane przy zakladaniu konta administratora. */
    boolean existsByRole(Role role);

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

    /**
     * Znajomi danej osoby, <b>od najbardziej powiazanych z ogladajacym</b>.
     *
     * <p>To jedyne zapytanie NATYWNE (czyste SQL) w projekcie - i jest ku temu
     * powod. Wynik sortujemy po kolumnie, ktorej nigdzie nie ma: liczbie
     * wspolnych znajomych, liczonej osobno dla kazdego wiersza. JPQL operuje
     * na encjach i takich rzeczy nie wyrazi bez przekombinowanych sztuczek.
     * Lista wymagan dopuszcza oba warianty ("wlasne zapytania {@code @Query}
     * / natywne") - wymaganie nr 8.</p>
     *
     * <p><b>Jak liczymy wspolnych znajomych.</b> Tabela {@code user_friends}
     * trzyma kazda znajomosc dwoma wierszami (A→B i B→A). Laczymy ja wiec
     * sama ze soba po kolumnie {@code friend_id}: jesli ten sam czlowiek jest
     * znajomym i kandydata, i ogladajacego, para pasuje i liczy sie do sumy.</p>
     *
     * <p><b>Dlaczego to jest wazne.</b> Dzis wynik opiera sie na wspolnych
     * znajomych, bo tylko takie dane mamy. Gdy dojda ulubieni artysci ze
     * Spotify, do wyniku doliczymy wspolnych artystow i gatunki - zmieni sie
     * TYLKO to zapytanie. Metoda, jej typ zwracany i caly frontend zostaja
     * nietkniete.</p>
     *
     * @param wlasciciel czyja liste znajomych ogladamy
     * @param ogladajacy kto oglada - wzgledem niego liczymy wspolnych znajomych
     */
    @Query(
        value = """
                SELECT u.username            AS username,
                       u.avatar_file_name    AS avatarFileName,
                       (SELECT COUNT(*)
                          FROM user_friends kandydat
                          JOIN user_friends widz
                            ON kandydat.friend_id = widz.friend_id
                         WHERE kandydat.user_id = u.id
                           AND widz.user_id = (SELECT id FROM users WHERE username = :ogladajacy)
                       )                     AS wspolniZnajomi
                  FROM users u
                  JOIN user_friends uf ON uf.friend_id = u.id
                  JOIN users wl ON wl.id = uf.user_id
                 WHERE wl.username = :wlasciciel
                 ORDER BY wspolniZnajomi DESC, u.username ASC
                """,
        countQuery = """
                SELECT COUNT(*)
                  FROM user_friends uf
                  JOIN users wl ON wl.id = uf.user_id
                 WHERE wl.username = :wlasciciel
                """,
        nativeQuery = true)
    Page<FriendRow> znajomiPosortowani(@Param("wlasciciel") String wlasciciel,
                                       @Param("ogladajacy") String ogladajacy,
                                       Pageable pageable);

    /** Ilu znajomych ma dana osoba - liczba na profilu. */
    @Query(value = """
           SELECT COUNT(*)
             FROM user_friends uf
             JOIN users wl ON wl.id = uf.user_id
            WHERE wl.username = :username
           """, nativeQuery = true)
    long policzZnajomych(@Param("username") String username);

    /**
     * Czy dwie osoby sa juz znajomymi.
     *
     * <p>Sprawdzamy jeden kierunek, bo znajomosc zawsze zapisujemy dwoma
     * wierszami naraz (patrz {@code User.dodajZnajomego}).</p>
     */
    @Query(value = """
           SELECT COUNT(*) > 0
             FROM user_friends uf
             JOIN users a ON a.id = uf.user_id
             JOIN users b ON b.id = uf.friend_id
            WHERE a.username = :pierwszy AND b.username = :drugi
           """, nativeQuery = true)
    boolean czySaZnajomymi(@Param("pierwszy") String pierwszy, @Param("drugi") String drugi);
}
