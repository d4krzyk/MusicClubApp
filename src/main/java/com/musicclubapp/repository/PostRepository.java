package com.musicclubapp.repository;

import com.musicclubapp.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Dostep do postow. */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Tablica: najpierw znajomi, potem reszta swiata. Bez osob zablokowanych
     * przez ogladajacego i blokujacych go (:hidden).
     */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           LEFT JOIN FETCH p.event
           WHERE p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR a.id IN :circle)
             AND a.id NOT IN :hidden
           ORDER BY CASE WHEN a.id IN :circle THEN 0 ELSE 1 END, p.createdAt DESC
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
             AND p.author.id NOT IN :hidden
           """)
    Page<Post> findFeed(@Param("circle") Collection<Long> circle,
                        @Param("hidden") Collection<Long> hidden,
                        Pageable pageable);

    /*
     * Tablica "Dla ciebie" sklada sie z dwoch list: posty kregu (od najnowszych) i posty obcych (najpierw
     * 300 najnowszych porzadkowanych trafnoscia, reszta od najnowszych). Ten sam warunek widocznosci co
     * w findFeed - rozni sie tylko tym, ze lista jest podzielona na te dwie czesci.
     */

    /** Ile postow maja osoby z kregu (razem z moimi). */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.author.id IN :circle AND p.clan IS NULL")
    long countCircle(@Param("circle") Collection<Long> circle);

    /** Wycinek postow kregu, od najnowszych; remis dat rozstrzyga numer, zeby kolejnosc byla jednoznaczna. */
    @Query("""
           SELECT p FROM Post p
           JOIN FETCH p.author a
           LEFT JOIN FETCH p.event
           WHERE a.id IN :circle AND p.clan IS NULL
           ORDER BY p.createdAt DESC, p.id DESC
           """)
    List<Post> circleSlice(@Param("circle") Collection<Long> circle, Pageable pageable);

    /** Ile postow maja osoby spoza kregu, ktore ogladajacy moze zobaczyc (publiczne, bez blokad). */
    @Query("""
           SELECT COUNT(p) FROM Post p
           WHERE p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC OR p.visibility IS NULL)
             AND p.author.id NOT IN :circle
             AND p.author.id NOT IN :hidden
           """)
    long countStrangers(@Param("circle") Collection<Long> circle, @Param("hidden") Collection<Long> hidden);

    /** Wycinek postow obcych, od najnowszych. */
    @Query("""
           SELECT p FROM Post p
           JOIN FETCH p.author a
           LEFT JOIN FETCH p.event
           WHERE p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC OR p.visibility IS NULL)
             AND a.id NOT IN :circle
             AND a.id NOT IN :hidden
           ORDER BY p.createdAt DESC, p.id DESC
           """)
    List<Post> strangersSlice(@Param("circle") Collection<Long> circle,
                              @Param("hidden") Collection<Long> hidden, Pageable pageable);

    /** Tablica zawezona do wlasnego kregu - wybor uzytkownika w przelaczniku nad tablica. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           LEFT JOIN FETCH p.event
           WHERE a.id IN :circle AND p.clan IS NULL
           ORDER BY p.createdAt DESC
           """,
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id IN :circle AND p.clan IS NULL")
    Page<Post> findCircleFeed(@Param("circle") Collection<Long> circle, Pageable pageable);

    /** Posty jednego uzytkownika - do jego profilu. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           LEFT JOIN FETCH p.event
           WHERE a.username = :username AND p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR a.id IN :circle)
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE p.author.username = :username AND p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
           """)
    Page<Post> findByAuthorUsername(@Param("username") String username,
                                    @Param("circle") Collection<Long> circle,
                                    Pageable pageable);

    /** Posty pod jednym wydarzeniem - te same warunki widocznosci co na tablicy. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           LEFT JOIN FETCH p.event e
           WHERE e.id = :eventId AND p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR a.id IN :circle)
             AND a.id NOT IN :hidden
           ORDER BY p.createdAt DESC
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE p.event.id = :eventId AND p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
             AND p.author.id NOT IN :hidden
           """)
    Page<Post> findByEvent(@Param("eventId") Long eventId,
                           @Param("circle") Collection<Long> circle,
                           @Param("hidden") Collection<Long> hidden,
                           Pageable pageable);

    /** Posty klanu, najnowsze na gorze; bez osob z blokad ogladajacego. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           JOIN FETCH p.clan
           WHERE p.clan.id = :clanId AND a.id NOT IN :hidden
           ORDER BY p.createdAt DESC
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE p.clan.id = :clanId AND p.author.id NOT IN :hidden
           """)
    Page<Post> findByClan(@Param("clanId") Long clanId,
                          @Param("hidden") Collection<Long> hidden,
                          Pageable pageable);

    /** Wszystkie posty klanu - przy jego rozwiazywaniu. */
    List<Post> findByClanId(Long clanId);

    /** Ktore z tych wydarzen klan juz "zapytal" postem - numer najnowszego takiego posta. */
    @Query("""
           SELECT p.event.id AS eventId, MAX(p.id) AS total FROM Post p
           WHERE p.clan.id = :clanId AND p.event.id IN :eventIds
           GROUP BY p.event.id
           """)
    List<EventCountRow> clanPostsUnderEvents(@Param("clanId") Long clanId,
                                             @Param("eventIds") java.util.Collection<Long> eventIds);


    /**
     * Post razem z autorem - uzywane przy usuwaniu, zeby sprawdzic wlasciciela bez dodatkowego
     * zapytania do bazy.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author LEFT JOIN FETCH p.event LEFT JOIN FETCH p.clan WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(@Param("id") Long id);

    /** Ile postow napisal dany uzytkownik - liczba na jego profilu. */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.author.username = :username AND p.clan IS NULL")
    long countByAuthorUsername(@Param("username") String username);

    /** Ile postow tej osoby widzi konkretny ogladajacy - liczba na profilu. */
    @Query("""
           SELECT COUNT(p) FROM Post p
           WHERE p.author.username = :username AND p.clan IS NULL
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
           """)
    long countVisibleFor(@Param("username") String username,
                         @Param("circle") Collection<Long> circle);

    /** Najczesciej wrzucane przez uzytkownika nagrania danego rodzaju. */
    @Query(value = """
           SELECT p.music_provider          AS provider,
                  p.music_kind              AS kind,
                  p.music_external_id       AS externalId,
                  MAX(p.music_title)        AS title,
                  MAX(p.music_thumbnail_url) AS thumbnailUrl,
                  COUNT(*)                  AS timesPosted
             FROM posts p
             JOIN users u ON u.id = p.author_id
            WHERE u.username = :username
              AND p.music_external_id IS NOT NULL
              AND p.clan_id IS NULL
              AND p.music_kind = :kind
            GROUP BY p.music_provider, p.music_kind, p.music_external_id
            ORDER BY timesPosted DESC, MAX(p.created_at) DESC
            LIMIT :limit
           """, nativeQuery = true)
    List<TopMusicRow> mostPosted(@Param("username") String username,
                                          @Param("kind") String kind,
                                          @Param("limit") int limit);

    /** Wszystkie posty jednego autora - przy usuwaniu konta. */
    List<Post> findByAuthorId(Long authorId);
}
