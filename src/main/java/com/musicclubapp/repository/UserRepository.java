package com.musicclubapp.repository;

import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
                       )                     AS sharedFriends
                  FROM users u
                  JOIN user_friends uf ON uf.friend_id = u.id
                  JOIN users wl ON wl.id = uf.user_id
                 WHERE wl.username = :wlasciciel
                 ORDER BY sharedFriends DESC, u.username ASC
                """,
        countQuery = """
                SELECT COUNT(*)
                  FROM user_friends uf
                  JOIN users wl ON wl.id = uf.user_id
                 WHERE wl.username = :wlasciciel
                """,
        nativeQuery = true)
    Page<FriendRow> friendsRanked(@Param("wlasciciel") String owner,
                                       @Param("ogladajacy") String viewer,
                                       Pageable pageable);

    /**
     * <b>Proponowani znajomi: WSZYSCY uzytkownicy, posortowani od najlepiej
     * dopasowanych.</b>
     *
     * <p>Nie filtrujemy nikogo poza samym pytajacym i kontami wylaczonymi.
     * Taki byl zamysl: lista ma pokazac cala spolecznosc - najpierw osoby
     * o podobnym guscie, dalej reszta. Aplikacja dla kilkunastu osob, ktora
     * po odfiltrowaniu "niedopasowanych" pokazuje pusta strone, jest
     * bezuzyteczna dokladnie na starcie, czyli wtedy, kiedy najbardziej
     * potrzeba w niej ludzi.</p>
     *
     * <p><b>Skad bierze sie wynik.</b> Trzy skladniki, kazdy liczony osobno
     * dla kazdego kandydata:</p>
     * <table><caption>Wagi</caption>
     *   <tr><td>wspolny artysta</td><td>×5</td>
     *       <td>sygnal najmocniejszy - to konkretna, swiadoma deklaracja</td></tr>
     *   <tr><td>wspolny znajomy</td><td>×3</td>
     *       <td>sygnal spoleczny; nie o muzyce, ale trafny</td></tr>
     *   <tr><td>wspolny gatunek</td><td>×1</td>
     *       <td>najslabszy - "rock" laczy polowe uzytkownikow</td></tr>
     * </table>
     *
     * <p>Wagi sa <b>umowne</b> i nie ma sposobu, zeby wyliczyc te "wlasciwe" -
     * wazne jest tylko, ze stoja w jednym miejscu i ich kolejnosc da sie
     * uzasadnic. Gatunek dostaje 1, bo inaczej ktos z pieciu gatunkow
     * przebijalby osobe, z ktora naprawde slucha sie tego samego zespolu.</p>
     *
     * <p><b>Podzapytania skorelowane</b> (te w nawiasach po SELECT) wykonuja
     * sie raz na kazdy wiersz wyniku. Przy tysiacach uzytkownikow bylby to
     * problem i trzeba by to przepisac na zlaczenia z grupowaniem; przy skali
     * tego projektu - kilkanascie do kilkuset kont - czytelnosc jest wazniejsza
     * niz mikrosekundy, a limit wierszy i tak narzuca {@code Pageable}.</p>
     *
     * @param ogladajacy login osoby, ktorej proponujemy znajomych
     */
    @Query(value = """
           SELECT t.*,
                  (5 * t.sharedArtists + 3 * t.sharedFriends + t.sharedGenres) AS score
             FROM (
                   SELECT u.username         AS username,
                          u.avatar_file_name AS avatarFileName,

                          (SELECT COUNT(*)
                             FROM user_friends kandydat
                             JOIN user_friends widz
                               ON kandydat.friend_id = widz.friend_id
                            WHERE kandydat.user_id = u.id
                              AND widz.user_id = ja.id)          AS sharedFriends,

                          (SELECT COUNT(*)
                             FROM user_favorite_artists kandydat
                             JOIN user_favorite_artists widz
                               ON kandydat.artist_id = widz.artist_id
                            WHERE kandydat.user_id = u.id
                              AND widz.user_id = ja.id)          AS sharedArtists,

                          (SELECT COUNT(DISTINCT g.genre)
                             FROM user_favorite_artists kandydat
                             JOIN artist_genres g ON g.artist_id = kandydat.artist_id
                            WHERE kandydat.user_id = u.id
                              AND g.genre IN (SELECT g2.genre
                                                FROM user_favorite_artists widz
                                                JOIN artist_genres g2
                                                  ON g2.artist_id = widz.artist_id
                                               WHERE widz.user_id = ja.id))
                                                                 AS sharedGenres,

                          EXISTS (SELECT 1
                                    FROM user_friends f
                                   WHERE f.user_id = ja.id
                                     AND f.friend_id = u.id)     AS alreadyFriend
                     FROM users u
                     CROSS JOIN (SELECT id FROM users WHERE username = :ogladajacy) ja
                    WHERE u.id <> ja.id
                      AND u.enabled = true
                  ) t
            ORDER BY score DESC, t.sharedArtists DESC, t.username ASC
           """, nativeQuery = true)
    List<SuggestionRow> friendSuggestions(@Param("ogladajacy") String viewer,
                                            Pageable pageable);

    /** Ilu znajomych ma dana osoba - liczba na profilu. */
    @Query(value = """
           SELECT COUNT(*)
             FROM user_friends uf
             JOIN users wl ON wl.id = uf.user_id
            WHERE wl.username = :username
           """, nativeQuery = true)
    long countFriends(@Param("username") String username);

    /**
     * Czy dwie osoby sa juz znajomymi.
     *
     * <p>Sprawdzamy jeden kierunek, bo znajomosc zawsze zapisujemy dwoma
     * wierszami naraz (patrz {@code User.addFriend}).</p>
     */
    @Query(value = """
           SELECT COUNT(*) > 0
             FROM user_friends uf
             JOIN users a ON a.id = uf.user_id
             JOIN users b ON b.id = uf.friend_id
            WHERE a.username = :pierwszy AND b.username = :drugi
           """, nativeQuery = true)
    boolean areFriends(@Param("pierwszy") String first, @Param("drugi") String second);

    /**
     * Wszystkie gatunki ulubionych wykonawcow danej osoby - <b>jednym zapytaniem</b>.
     *
     * <p>Da sie to policzyc w Javie: przejsc po ulubionych artystach i zebrac
     * ich gatunki. Kosztuje to jednak <b>osobne zapytanie na kazdego artyste</b>
     * (gatunki to leniwa kolekcja elementow), czyli przy trzydziestu ulubionych -
     * trzydziesci zapytan na jedno wejscie na profil. Tutaj jest jedno.</p>
     *
     * <p>{@code DISTINCT} jest potrzebny, bo ten sam gatunek ma zwykle wielu
     * wykonawcow z listy.</p>
     */
    @Query("""
           SELECT DISTINCT g FROM User u
           JOIN u.favoriteArtists a
           JOIN a.genres g
           WHERE u.username = :username
           """)
    List<String> genresOf(@Param("username") String username);

    /**
     * <b>"Moj krag"</b> - identyfikatory: moj wlasny i wszystkich moich znajomych.
     *
     * <p>Jedno pojecie, ktore zalatwia trzy sprawy naraz na tablicy: kogo posty
     * ida na gore, czyje posty "tylko dla znajomych" wolno mi zobaczyc i co
     * zostaje po zawezeniu tablicy do znajomych. Gdyby te trzy rzeczy liczyly
     * sie osobno, kazda mogla by sie rozjechac z pozostalymi.</p>
     *
     * <p><b>Wlasny identyfikator jest w wyniku celowo</b>, z dwoch powodow.
     * Merytorycznie: wlasne posty maja byc na gorze razem z postami znajomych,
     * a nie w czesci "obcy ludzie". Technicznie: dzieki temu zbior nigdy nie
     * jest pusty, a {@code IN ()} z pusta lista jest w SQL-u bledem skladni -
     * uzytkownik bez znajomych wywracalby cala tablice.</p>
     *
     * <p>{@code UNION} (a nie {@code UNION ALL}) usuwa ewentualne powtorzenia.</p>
     */
    @Query(value = """
           SELECT u.id FROM users u WHERE u.username = :username
           UNION
           SELECT uf.friend_id
             FROM user_friends uf
             JOIN users a ON a.id = uf.user_id
            WHERE a.username = :username
           """, nativeQuery = true)
    List<Long> circleIds(@Param("username") String username);

    /**
     * Kasuje wiersze znajomosci wskazujace na dane konto - <b>z obu stron</b>.
     *
     * <p>Znajomosc zapisujemy dwoma wierszami, zeby dalo sie ja czytac
     * w kazda strone jednym zapytaniem. Przy kasowaniu konta Hibernate
     * sprzata tylko te, w ktorych ta osoba jest wlascicielem relacji;
     * o drugiej polowie trzeba powiedziec wprost, inaczej u jej znajomych
     * zostalby wpis wskazujacy na nieistniejacego uzytkownika.</p>
     *
     * <p>Zapytanie natywne, bo {@code user_friends} to tabela laczaca -
     * nie ma dla niej encji, wiec JPQL nie ma sie do czego odwolac.</p>
     */
    @Modifying
    @Query(value = "DELETE FROM user_friends WHERE user_id = :userId OR friend_id = :userId",
           nativeQuery = true)
    void removeFriendshipsWith(@Param("userId") Long userId);
}
