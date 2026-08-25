package com.musicclubapp.repository;

import com.musicclubapp.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Dostep do postow.
 *
 * <p>Realizuje wymaganie nr 8 (wlasne zapytania {@code @Query}) oraz
 * nr 3 i 5 (stronicowanie i sortowanie po stronie bazy).</p>
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Tablica postow - wszystkie wpisy, od najnowszych.
     *
     * <p><b>Po co {@code JOIN FETCH p.author}?</b> Autor jest ladowany leniwie
     * ({@code FetchType.LAZY}), wiec bez tego Hibernate pobralby najpierw
     * posty, a potem osobne zapytanie po autora KAZDEGO z nich. Przy 20 postach
     * to 21 zapytan zamiast jednego - klasyczny problem N+1.</p>
     *
     * <p>Zdjec celowo NIE dolaczamy tutaj przez {@code JOIN FETCH}: laczenie
     * dwoch kolekcji naraz kazaloby bazie zwrocic iloczyn wierszy, przez co
     * stronicowanie liczyloby zle. Zdjecia doczytujemy osobno w serwisie.</p>
     */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author
           """,
           countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findFeed(Pageable pageable);

    /** Posty jednego uzytkownika - do jego profilu. */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           WHERE a.username = :username
           """,
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.username = :username")
    Page<Post> findByAuthorUsername(@Param("username") String username, Pageable pageable);

    /**
     * Post razem z autorem - uzywane przy usuwaniu, zeby sprawdzic wlasciciela
     * bez dodatkowego zapytania do bazy.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(@Param("id") Long id);

    /**
     * Ile postow napisal dany uzytkownik - liczba na jego profilu.
     *
     * <p>Nazwa metody wystarczy Springowi do zbudowania zapytania, wiec
     * {@code @Query} nie jest tu potrzebne. Liczymy w bazie zamiast pobierac
     * posty i wywolywac {@code size()} - inaczej wyswietlenie samej liczby
     * ciagneloby przez siec wszystkie wpisy razem z trescia.</p>
     */
    long countByAuthorUsername(String username);
}
