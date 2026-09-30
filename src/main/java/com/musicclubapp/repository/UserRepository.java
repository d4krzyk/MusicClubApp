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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repozytorium = warstwa dostepu do bazy. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Metoda pochodna (derived query) - Spring Data czyta NAZWE metody i sam buduje z niej SQL.
     * "findByUsername" -&gt; WHERE username = ?.
     */
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /** Przydaje sie przy rejestracji: "czy ten login jest juz zajety?" */
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * "Jan@Example.com" i "jan@example.com" to w praktyce ta sama skrzynka -
     * inaczej jeden adres dalby sie zarejestrowac kilka razy.
     */
    boolean existsByEmailIgnoreCase(String email);

    /** Konto po adresie bez wielkosci liter - do resetu hasla. */
    Optional<User> findFirstByEmailIgnoreCase(String email);

    /**
     * Sam znacznik bezpieczenstwa - sprawdzany przy kazdym zapytaniu
     * zalogowanego, wiec bez wczytywania calej encji. Pusty wynik = konta
     * juz nie ma; pusty znacznik (konto sprzed tej funkcji) = "".
     */
    @Query("SELECT COALESCE(u.securityStamp, '') FROM User u WHERE u.username = :username")
    Optional<String> securityStampOf(@Param("username") String username);

    /** Konta, ktore nie potwierdzily adresu od podanej chwili - do sprzatania. */
    @Query("SELECT u FROM User u WHERE u.emailVerifiedAt IS NULL AND u.createdAt < :before")
    List<User> unverifiedCreatedBefore(@Param("before") LocalDateTime before);

    /**
     * Gdy serwer nie wysyla poczty, nikt nie ma jak potwierdzic adresu - wiec
     * wszyscy licza sie jako potwierdzeni od chwili zalozenia konta. Po
     * wlaczeniu poczty potwierdzac musza tylko nowi.
     */
    @Modifying
    @Query("UPDATE User u SET u.emailVerifiedAt = u.createdAt WHERE u.emailVerifiedAt IS NULL")
    int verifyAllUnverified();

    /** Czy istnieje juz ktos z podana rola - uzywane przy zakladaniu konta administratora. */
    boolean existsByRole(Role role);

    /** Wszyscy z dana rola. */
    List<User> findByRole(Role role);

    /** Wlasne zapytanie JPQL - wymaganie nr 8. */
    @Query("""
           SELECT u FROM User u
           WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :fragment, '%'))
              OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :fragment, '%'))
           """)
    Page<User> searchByUsernameOrEmail(@Param("fragment") String fragment, Pageable pageable);

    /** Znajomi danej osoby, od najbardziej powiazanych z ogladajacym. */
    @Query(
        value = """
                SELECT u.username            AS username,
                       u.avatar_file_name    AS avatarFileName,
                       u.last_seen_at        AS lastSeenAt,
                       u.show_online         AS showOnline,
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
                   AND u.id NOT IN (:ukryci)
                 ORDER BY sharedFriends DESC, u.username ASC
                """,
        countQuery = """
                SELECT COUNT(*)
                  FROM user_friends uf
                  JOIN users wl ON wl.id = uf.user_id
                 WHERE wl.username = :wlasciciel
                   AND uf.friend_id NOT IN (:ukryci)
                """,
        nativeQuery = true)
    Page<FriendRow> friendsRanked(@Param("wlasciciel") String owner,
                                       @Param("ogladajacy") String viewer,
                                       @Param("ukryci") java.util.Collection<Long> hidden,
                                       Pageable pageable);

    /** Proponowani znajomi: WSZYSCY uzytkownicy, posortowani od najlepiej dopasowanych. */
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
                      -- blokady w obie strony
                      AND NOT EXISTS (SELECT 1 FROM user_blocks b
                                       WHERE (b.blocker_id = ja.id AND b.blocked_id = u.id)
                                          OR (b.blocker_id = u.id AND b.blocked_id = ja.id))
                      -- kto nie chce byc proponowany, nie jest - chyba ze to juz znajomy
                      AND (u.show_in_suggestions = true
                           OR EXISTS (SELECT 1 FROM user_friends f2
                                       WHERE f2.user_id = ja.id AND f2.friend_id = u.id))
                  ) t
            ORDER BY score DESC, t.sharedArtists DESC, t.username ASC
           """, nativeQuery = true)
    List<SuggestionRow> friendSuggestions(@Param("ogladajacy") String viewer,
                                            Pageable pageable);

    /** Ilu wspolnych znajomych maja dwie osoby - do zasady "tylko znajomi znajomych". */
    @Query(value = """
           SELECT COUNT(*)
             FROM user_friends a
             JOIN user_friends b ON a.friend_id = b.friend_id
            WHERE a.user_id = :pierwszy AND b.user_id = :drugi
           """, nativeQuery = true)
    long countSharedFriends(@Param("pierwszy") Long first, @Param("drugi") Long second);

    /** Ilu znajomych ma dana osoba - liczba na profilu. */
    @Query(value = """
           SELECT COUNT(*)
             FROM user_friends uf
             JOIN users wl ON wl.id = uf.user_id
            WHERE wl.username = :username
           """, nativeQuery = true)
    long countFriends(@Param("username") String username);

    /** Czy dwie osoby sa juz znajomymi. */
    @Query(value = """
           SELECT COUNT(*) > 0
             FROM user_friends uf
             JOIN users a ON a.id = uf.user_id
             JOIN users b ON b.id = uf.friend_id
            WHERE a.username = :pierwszy AND b.username = :drugi
           """, nativeQuery = true)
    boolean areFriends(@Param("pierwszy") String first, @Param("drugi") String second);

    /** Wszystkie gatunki ulubionych wykonawcow danej osoby - jednym zapytaniem. */
    @Query("""
           SELECT DISTINCT g FROM User u
           JOIN u.favoriteArtists a
           JOIN a.genres g
           WHERE u.username = :username
           """)
    List<String> genresOf(@Param("username") String username);

    /**
     * Gatunki ulubionych wykonawcow Z POWTORZENIAMI - tag, ktory ma pieciu
     * moich artystow, pojawia sie piec razy. Z tego liczymy, jak mocno dany
     * gatunek jest "moj", przy dopasowaniu wydarzen.
     */
    @Query("""
           SELECT g FROM User u
           JOIN u.favoriteArtists a
           JOIN a.genres g
           WHERE u.username = :username
           """)
    List<String> genreTagsOfFavorites(@Param("username") String username);

    /**
     * Wykonawcy, ktorych lubi co najmniej {@code min} osob z tej grupy - z liczba tych osob.
     * Zestawienie klanu: nie wskazuje, KTO ich lubi, a osoby z ograniczonym profilem
     * (tylko dla znajomych) nie sa do niego wliczane - nie po to je ukryly.
     */
    @Query("""
           SELECT a.externalId AS externalId, a.name AS name, a.imageUrl AS imageUrl,
                  COUNT(DISTINCT u.id) AS total
           FROM User u JOIN u.favoriteArtists a
           WHERE u.id IN :userIds AND u.profileVisibility = com.musicclubapp.entity.ProfileVisibility.EVERYONE
           GROUP BY a.id, a.externalId, a.name, a.imageUrl
           HAVING COUNT(DISTINCT u.id) >= :min
           ORDER BY COUNT(DISTINCT u.id) DESC, a.name
           """)
    List<TasteArtistRow> commonArtists(@Param("userIds") Collection<Long> userIds, @Param("min") long min,
                                       Pageable limit);

    /** Gatunki wspolne dla co najmniej {@code min} osob z grupy - te same zasady co wyzej. */
    @Query("""
           SELECT g AS name, COUNT(DISTINCT u.id) AS total
           FROM User u JOIN u.favoriteArtists a JOIN a.genres g
           WHERE u.id IN :userIds AND u.profileVisibility = com.musicclubapp.entity.ProfileVisibility.EVERYONE
           GROUP BY g
           HAVING COUNT(DISTINCT u.id) >= :min
           ORDER BY COUNT(DISTINCT u.id) DESC, g
           """)
    List<TasteGenreRow> commonGenres(@Param("userIds") Collection<Long> userIds, @Param("min") long min,
                                     Pageable limit);

    /** Ile z tych osob ma jawny profil - tyle wchodzi do zestawienia gustow. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN :userIds AND u.profileVisibility = com.musicclubapp.entity.ProfileVisibility.EVERYONE")
    long countOpenProfiles(@Param("userIds") Collection<Long> userIds);

    /** Kraje wydarzen wybrane na kontach - od najczesciej wybieranego. Do importu. */
    @Query("""
           SELECT u.eventsCountry FROM User u
           WHERE u.eventsCountry IS NOT NULL
           GROUP BY u.eventsCountry
           ORDER BY COUNT(u) DESC
           """)
    List<String> eventCountriesInUse();

    /** Identyfikatory znajomych - do licznikow "ilu znajomych idzie". */
    @Query("SELECT f.id FROM User u JOIN u.friends f WHERE u.username = :username")
    List<Long> friendIdsOf(@Param("username") String username);

    /** "Moj krag" - identyfikatory: moj wlasny i wszystkich moich znajomych. */
    @Query(value = """
           SELECT u.id FROM users u WHERE u.username = :username
           UNION
           SELECT uf.friend_id
             FROM user_friends uf
             JOIN users a ON a.id = uf.user_id
            WHERE a.username = :username
           """, nativeQuery = true)
    List<Long> circleIds(@Param("username") String username);

    /** Zapisuje date ostatniej aktywnosci - jednym zapytaniem, bez wczytywania encji. */
    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :now WHERE u.username = :username")
    void touchLastSeen(@Param("username") String username, @Param("now") java.time.LocalDateTime now);

    /** Kasuje wiersze znajomosci wskazujace na dane konto - z obu stron. */
    @Modifying
    @Query(value = "DELETE FROM user_friends WHERE user_id = :userId OR friend_id = :userId",
           nativeQuery = true)
    void removeFriendshipsWith(@Param("userId") Long userId);
}
