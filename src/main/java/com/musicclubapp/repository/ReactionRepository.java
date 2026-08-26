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

/**
 * Dostep do reakcji na posty.
 *
 * <p>Kolejne wlasne zapytania {@code @Query} (wymaganie nr 8) - tym razem
 * z grupowaniem.</p>
 */
@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    /**
     * Liczniki reakcji dla CALEJ strony postow naraz.
     *
     * <p><b>Dlaczego jedno zapytanie na cala strone, a nie jedno na post?</b>
     * Przy dwudziestu postach dostalibysmy dwadziescia zapytan do bazy zamiast
     * jednego (problem N+1). Baza policzy to sama i odesle gotowe sumy -
     * a przesylanych wierszy jest tyle, ile faktycznie uzytych reakcji,
     * czyli najwyzej trzy na post.</p>
     *
     * <p>{@code SELECT new ...} to <b>wyrazenie konstruktora</b> JPQL: wynik
     * kazdego wiersza trafia od razu do rekordu {@link ReactionCount},
     * zamiast wracac jako tablica {@code Object[]}.</p>
     */
    @Query("""
           SELECT new com.musicclubapp.repository.ReactionCount(r.post.id, r.type, COUNT(r))
           FROM Reaction r
           WHERE r.post.id IN :postIds
           GROUP BY r.post.id, r.type
           """)
    List<ReactionCount> countForPosts(@Param("postIds") Collection<Long> postIds);

    /**
     * Reakcje JEDNEGO uzytkownika na podane posty - zeby podswietlic
     * przycisk, ktory sam wybral.
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
     * Kasuje wszystkie reakcje jednej osoby - przy usuwaniu konta.
     *
     * <p>{@code @Modifying} jest tu obowiazkowe: bez niego Spring probowalby
     * potraktowac to jako zapytanie czytajace i odmowilby wykonania.</p>
     */
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
