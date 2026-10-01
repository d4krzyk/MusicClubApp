package com.musicclubapp.repository;

import com.musicclubapp.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Komentarze. {@code :hidden} to osoby z blokad ogladajacego (w obie strony; pusta lista jest podmieniana
 * na -1). Komentarz osoby ukrytej nie istnieje, a razem z nim jego watek: odpowiedzi innych osob pod nim
 * tez znikaja (odpowiadaly na cos, czego ogladajacy nie widzi).
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Komentarze pierwszego poziomu pod postem, od najnowszych. */
    @Query(value = """
           SELECT c FROM Comment c
           JOIN FETCH c.author
           WHERE c.post.id = :postId AND c.parent IS NULL AND c.author.id NOT IN :hidden
           ORDER BY c.createdAt DESC, c.id DESC
           """,
           countQuery = """
           SELECT COUNT(c) FROM Comment c
           WHERE c.post.id = :postId AND c.parent IS NULL AND c.author.id NOT IN :hidden
           """)
    Page<Comment> roots(@Param("postId") Long postId, @Param("hidden") Collection<Long> hidden, Pageable pageable);

    /** Odpowiedzi pod komentarzem, od najstarszej (rozmowa czyta sie z gory na dol). */
    @Query(value = """
           SELECT c FROM Comment c
           JOIN FETCH c.author
           LEFT JOIN FETCH c.replyTo
           WHERE c.parent.id = :parentId AND c.author.id NOT IN :hidden
           ORDER BY c.createdAt ASC, c.id ASC
           """,
           countQuery = """
           SELECT COUNT(c) FROM Comment c
           WHERE c.parent.id = :parentId AND c.author.id NOT IN :hidden
           """)
    Page<Comment> replies(@Param("parentId") Long parentId, @Param("hidden") Collection<Long> hidden, Pageable pageable);

    /** Ile odpowiedzi ma kazdy z tych komentarzy (bez odpowiedzi osob z blokad). */
    @Query("""
           SELECT c.parent.id AS ownerId, COUNT(c) AS total FROM Comment c
           WHERE c.parent.id IN :ids AND c.author.id NOT IN :hidden
           GROUP BY c.parent.id
           """)
    List<CommentCountRow> replyCounts(@Param("ids") Collection<Long> ids, @Param("hidden") Collection<Long> hidden);

    /** Wszystkie komentarze pod postami (z odpowiedziami), takie, jakie widzi ogladajacy. */
    @Query("""
           SELECT c.post.id AS ownerId, COUNT(c) AS total
           FROM Comment c LEFT JOIN c.parent p
           WHERE c.post.id IN :postIds AND c.author.id NOT IN :hidden
             AND (p IS NULL OR p.author.id NOT IN :hidden)
           GROUP BY c.post.id
           """)
    List<CommentCountRow> countByPosts(@Param("postIds") Collection<Long> postIds,
                                       @Param("hidden") Collection<Long> hidden);

    /** Komentarz razem z postem (i jego autorem i klanem), autorem i rodzicem - do sprawdzenia dostepu. */
    @Query("""
           SELECT c FROM Comment c
           JOIN FETCH c.post p
           JOIN FETCH p.author
           LEFT JOIN FETCH p.clan
           JOIN FETCH c.author
           LEFT JOIN FETCH c.parent
           LEFT JOIN FETCH c.replyTo
           WHERE c.id = :id
           """)
    Optional<Comment> findWithContext(@Param("id") Long id);

    /** Ile komentarzy napisala ta osoba od podanej chwili - limit szybkosci pisania. */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.author.id = :authorId AND c.createdAt > :since")
    long countSince(@Param("authorId") Long authorId, @Param("since") LocalDateTime since);

    /** Kto komentowal ten post, od ostatnio piszacego - do podpowiedzi przy oznaczaniu. */
    @Query("SELECT c.author.id FROM Comment c WHERE c.post.id = :postId GROUP BY c.author.id ORDER BY MAX(c.id) DESC")
    List<Long> authorIdsOnPost(@Param("postId") Long postId);

    /** Komentarze tej osoby razem z postami - do pobrania wlasnych danych. */
    @Query("""
           SELECT c FROM Comment c
           JOIN FETCH c.post p JOIN FETCH p.author
           LEFT JOIN FETCH c.parent
           WHERE c.author.id = :userId ORDER BY c.createdAt
           """)
    List<Comment> ofUser(@Param("userId") Long userId);

    /** Wszystkie komentarze tej osoby - przy usuwaniu konta (odpowiedzi pod nimi, oznaczenia i powiadomienia znikaja z nimi). */
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.author.id = :userId")
    void deleteByAuthorId(@Param("userId") Long userId);
}
