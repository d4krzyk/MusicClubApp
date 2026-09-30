package com.musicclubapp.repository;

import com.musicclubapp.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Dostep do reakcji na posty. */
@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    /** Liczniki reakcji dla CALEJ strony postow naraz. */
    @Query("""
           SELECT new com.musicclubapp.repository.ReactionCount(r.post.id, r.type, COUNT(r))
           FROM Reaction r
           WHERE r.post.id IN :postIds
           GROUP BY r.post.id, r.type
           """)
    List<ReactionCount> countForPosts(@Param("postIds") Collection<Long> postIds);

    /**
     * Reakcje JEDNEGO uzytkownika na podane posty - zeby podswietlic przycisk, ktory sam wybral.
     */
    @Query("""
           SELECT r FROM Reaction r
           WHERE r.post.id IN :postIds AND r.user.username = :username
           """)
    List<Reaction> findOwn(@Param("postIds") Collection<Long> postIds,
                                @Param("username") String username);

    /** Reakcja konkretnej osoby na konkretny post - przy dodawaniu i zmianie zdania. */
    @Query("""
           SELECT r FROM Reaction r
           WHERE r.post.id = :postId AND r.user.username = :username
           """)
    Optional<Reaction> find(@Param("postId") Long postId,
                              @Param("username") String username);

    /**
     * Kasuje wszystkie reakcje jednej osoby - przy usuwaniu konta. @Modifying jest tu obowiazkowe:
     * bez niego Spring probowalby potraktowac to jako zapytanie czytajace i odmowilby wykonania.
     */
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** Kto zareagowal na dany post - do okienka "kto zareagowal". */
    @Query("""
           SELECT r FROM Reaction r
           JOIN FETCH r.user
           WHERE r.post.id = :postId
           ORDER BY r.createdAt DESC
           """)
    List<Reaction> findForPost(@Param("postId") Long postId);

    /** Kasuje wszystkie reakcje pod danym postem - przed jego usunieciem. */
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    /** Reakcje pod wszystkimi postami klanu - przed jego rozwiazaniem. */
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.post.id IN (SELECT p.id FROM Post p WHERE p.clan.id = :clanId)")
    void deleteByPostsOfClan(@Param("clanId") Long clanId);
}
