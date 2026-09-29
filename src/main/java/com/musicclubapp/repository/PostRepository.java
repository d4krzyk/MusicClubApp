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
           WHERE (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR a.id IN :circle)
             AND a.id NOT IN :hidden
           ORDER BY CASE WHEN a.id IN :circle THEN 0 ELSE 1 END, p.createdAt DESC
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
             AND p.author.id NOT IN :hidden
           """)
    Page<Post> findFeed(@Param("circle") Collection<Long> circle,
                        @Param("hidden") Collection<Long> hidden,
                        Pageable pageable);

    /** Tablica zawezona do wlasnego kregu - wybor uzytkownika w przelaczniku nad tablica. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           WHERE a.id IN :circle
           ORDER BY p.createdAt DESC
           """,
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id IN :circle")
    Page<Post> findCircleFeed(@Param("circle") Collection<Long> circle, Pageable pageable);

    /** Posty jednego uzytkownika - do jego profilu. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           WHERE a.username = :username
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR a.id IN :circle)
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE p.author.username = :username
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
           """)
    Page<Post> findByAuthorUsername(@Param("username") String username,
                                    @Param("circle") Collection<Long> circle,
                                    Pageable pageable);

    /**
     * Post razem z autorem - uzywane przy usuwaniu, zeby sprawdzic wlasciciela bez dodatkowego
     * zapytania do bazy.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(@Param("id") Long id);

    /** Ile postow napisal dany uzytkownik - liczba na jego profilu. */
    long countByAuthorUsername(String username);

    /** Ile postow tej osoby widzi konkretny ogladajacy - liczba na profilu. */
    @Query("""
           SELECT COUNT(p) FROM Post p
           WHERE p.author.username = :username
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
