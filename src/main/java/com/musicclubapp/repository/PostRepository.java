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

/**
 * Dostep do postow.
 *
 * <p>Realizuje wymaganie nr 8 (wlasne zapytania {@code @Query}) oraz
 * nr 3 i 5 (stronicowanie i sortowanie po stronie bazy).</p>
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * <b>Tablica: najpierw znajomi, potem reszta swiata.</b>
     *
     * <p><b>Po co {@code JOIN FETCH p.author}?</b> Autor jest ladowany leniwie
     * ({@code FetchType.LAZY}), wiec bez tego Hibernate pobralby najpierw
     * posty, a potem osobne zapytanie po autora KAZDEGO z nich. Przy 20 postach
     * to 21 zapytan zamiast jednego - klasyczny problem N+1.</p>
     *
     * <p>Zdjec celowo NIE dolaczamy tutaj przez {@code JOIN FETCH}: laczenie
     * dwoch kolekcji naraz kazaloby bazie zwrocic iloczyn wierszy, przez co
     * stronicowanie liczyloby zle. Zdjecia doczytujemy osobno w serwisie.</p>
     *
     * <p><b>Kolejnosc robi {@code ORDER BY CASE}</b>, a nie sortowanie
     * przekazane z kontrolera. Interesuje nas kolejnosc po polu, ktorego
     * w tabeli nie ma: "czy ten autor jest w moim kregu". Baza wylicza z tego
     * zero albo jedynke i po niej sortuje w pierwszej kolejnosci, a dopiero
     * w drugiej po dacie. Dzieki temu stronicowanie dziala normalnie: druga
     * strona zaczyna sie dokladnie tam, gdzie skonczyla pierwsza. Gdyby
     * przesiewac to w Javie po pobraniu, licznik stron klamalby przy kazdym
     * zapytaniu.</p>
     *
     * <p><b>{@code visibility IS NULL} traktujemy jak PUBLIC.</b> Pustka moze
     * zostac wylacznie po postach sprzed dolozenia tej kolumny; uzupelnia je
     * {@code PostVisibilityMigration} przy starcie. Warunek jest tu na wypadek,
     * gdyby to przepisanie nie doszlo do skutku - bez niego cala dotychczasowa
     * tablica zniknelaby uzytkownikom z oczu. Ta sama zasada jest zapisana
     * w {@code Post.getVisibility()}.</p>
     *
     * @param circle identyfikatory: moj i moich znajomych ({@code UserRepository.circleIds})
     */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           WHERE p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
              OR p.visibility IS NULL
              OR a.id IN :circle
           ORDER BY CASE WHEN a.id IN :circle THEN 0 ELSE 1 END, p.createdAt DESC
           """,
           countQuery = """
           SELECT COUNT(p) FROM Post p
           WHERE p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
              OR p.visibility IS NULL
              OR p.author.id IN :circle
           """)
    Page<Post> findFeed(@Param("circle") Collection<Long> circle, Pageable pageable);

    /**
     * Tablica zawezona do wlasnego kregu - wybor uzytkownika w przelaczniku
     * nad tablica.
     *
     * <p>Tu <b>nie ma juz warunku widocznosci</b> i nie jest to niedopatrzenie:
     * wszystko, co napisali moi znajomi i ja, moge zobaczyc niezaleznie od
     * tego, dla kogo bylo przeznaczone. Doklejenie tego warunku niczego by nie
     * zmienilo poza dluzszym zapytaniem.</p>
     */
    @Query(value = """
           SELECT p FROM Post p
           JOIN FETCH p.author a
           WHERE a.id IN :circle
           ORDER BY p.createdAt DESC
           """,
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id IN :circle")
    Page<Post> findCircleFeed(@Param("circle") Collection<Long> circle, Pageable pageable);

    /**
     * Posty jednego uzytkownika - do jego profilu.
     *
     * <p>Posty "tylko dla znajomych" widac tu wylacznie wtedy, gdy autor jest
     * w moim kregu. Bez tego warunku profil byloby najprostsza obejsciem calej
     * reguly: wystarczyloby wejsc na czyjs profil zamiast na tablice.</p>
     */
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

    /**
     * Ile postow tej osoby <b>widzi konkretny ogladajacy</b> - liczba na profilu.
     *
     * <p>Osobna metoda obok {@link #countByAuthorUsername(String)}, bo obie
     * odpowiadaja na inne pytanie. Tamta mowi, ile ktos napisal (uzywamy jej
     * przy sprzataniu konta); ta - ile z tego mam prawo zobaczyc. Gdyby profil
     * pokazywal tamta liczbe, obok "12 postow" widnialoby siedem wpisow i cala
     * strona wygladalaby na zepsuta.</p>
     */
    @Query("""
           SELECT COUNT(p) FROM Post p
           WHERE p.author.username = :username
             AND (p.visibility = com.musicclubapp.entity.PostVisibility.PUBLIC
                  OR p.visibility IS NULL
                  OR p.author.id IN :circle)
           """)
    long countVisibleFor(@Param("username") String username,
                         @Param("circle") Collection<Long> circle);

    /**
     * Najczesciej wrzucane przez uzytkownika nagrania danego rodzaju.
     *
     * <p><b>Skad to sie bierze.</b> Nie prowadzimy osobnej tabeli statystyk -
     * liczymy to z postow, ktore i tak sa w bazie. Grupujemy po identyfikatorze
     * nagrania, bo to on jest staly: ten sam utwor wklejony raz przez
     * {@code youtu.be}, a raz przez {@code youtube.com/watch} ma w bazie
     * dokladnie te sama wartosc, bo adres rozkladamy na czesci przy zapisie.</p>
     *
     * <p>{@code MAX(title)} zamiast zwyklego {@code title}: przy {@code GROUP BY}
     * baza musi wiedziec, ktora z wielu wartosci wybrac. Tytul tego samego
     * nagrania jest zawsze taki sam, chyba ze przy ktoryms poscie nie udalo
     * sie go pobrac i jest pusty - a wtedy {@code MAX} wybierze ten
     * niepusty.</p>
     *
     * <p>Zapytanie natywne, bo {@code GROUP BY} z wyliczona kolumna
     * i sortowaniem po niej jest w SQL-u po prostu czytelniejszy.</p>
     */
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

    /**
     * Wszystkie posty jednego autora - przy usuwaniu konta.
     *
     * <p>Nie kasujemy ich jednym {@code DELETE}, bo najpierw trzeba zdjac
     * z dysku zdjecia, ktore do nich naleza. Wersja z pobraniem encji pozwala
     * tez kaskadzie zabrac zdjecia i cudze reakcje pod tymi postami.</p>
     */
    List<Post> findByAuthorId(Long authorId);
}
