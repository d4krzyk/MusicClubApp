package com.musicclubapp.repository;

import com.musicclubapp.entity.Report;
import com.musicclubapp.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Zgloszenia uzytkownikow. */
@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Panel administratora: zgloszenia o danym stanie, od najnowszych. */
    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    /** Wszystkie, gdy administrator zdejmie filtr. */
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Liczba czekajacych - to ona wisi przy ikonie panelu. */
    long countByStatus(ReportStatus status);

    /**
     * Czy zglaszajacy ma juz OTWARTE zgloszenie na te osobe.
     *
     * <p>Pierwsza z dwoch blokad przed zasypywaniem panelu: dopoki poprzednie
     * zgloszenie czeka na decyzje, kolejne na te sama osobe niczego nie wnosi.
     * Po zamknieciu mozna zglosic ponownie - bo wtedy chodzi juz
     * o <b>nowe</b> zdarzenie.</p>
     */
    boolean existsByReporterIdAndReportedIdAndStatus(Long reporterId, Long reportedId,
                                                     ReportStatus status);

    /**
     * Ile zgloszen ta osoba wyslala od podanej chwili.
     *
     * <p>Druga blokada: dzienny limit. Bez niej jeden uzytkownik moglby zglosic
     * po kolei wszystkich w serwisie - kazde zgloszenie osobne, wiec blokada
     * wyzej nie zadzialalaby ani razu.</p>
     */
    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime since);

    /**
     * Ile razy ta osoba byla zglaszana i uznana za winna.
     *
     * <p>Administrator widzi to przy nowym zgloszeniu. Jedno zgloszenie moze
     * byc nieporozumieniem; piate zasadne to juz wzorzec zachowania i zupelnie
     * inna decyzja.</p>
     */
    long countByReportedIdAndStatus(Long reportedId, ReportStatus status);

    /**
     * Ile zasadnych zgloszen ma KAZDE ze wskazanych kont - jednym zapytaniem.
     *
     * <p>Panel pokazuje te liczbe przy kazdym wierszu. Liczona osobno dla
     * kazdego konta oznaczalaby dwadziescia dodatkowych zapytan na jedno
     * wejscie do panelu.</p>
     *
     * <p>Konta bez ani jednego zasadnego zgloszenia w wyniku <b>nie wystapia</b> -
     * grupowanie nie tworzy grup pustych. Wolajacy musi wiec traktowac brak
     * wpisu jako zero, a nie jako blad.</p>
     */
    @Query("""
           SELECT new com.musicclubapp.repository.CountByUser(r.reported.id, COUNT(r))
           FROM Report r
           WHERE r.status = :status AND r.reported.id IN :userIds
           GROUP BY r.reported.id
           """)
    List<CountByUser> countByStatusForUsers(@Param("status") ReportStatus status,
                                            @Param("userIds") List<Long> userIds);

    /**
     * Kasuje zgloszenia zwiazane z kontem - <b>w obie strony</b>.
     *
     * <p>Przy usuwaniu konta. Zgloszenia wskazuja na uzytkownika dwoma
     * kluczami obcymi (zglaszajacy i zglaszany), wiec pozostawienie
     * ktoregokolwiek konczy sie odmowa bazy.</p>
     */
    @Modifying
    @Query("DELETE FROM Report r WHERE r.reporter.id = :userId OR r.reported.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);

    /**
     * Odpina posty od zgloszen przed skasowaniem tych postow.
     *
     * <p>Bez tego skasowanie konta konczy sie odmowa bazy: zgloszenie wskazuje
     * na post kluczem obcym. Odpinamy, zamiast kasowac zgloszenie - dotyczy
     * ono osoby, ktora moze wciaz istniec, a tresc posta jest i tak zapisana
     * w migawce dowodow.</p>
     */
    @Modifying
    @Query("UPDATE Report r SET r.post = null WHERE r.post.author.id = :authorId")
    void detachPostsOfAuthor(@Param("authorId") Long authorId);

    /**
     * Odpina JEDEN post od wszystkich zgloszen, ktore go wskazuja.
     *
     * <p><b>Blad, ktory to wymusil.</b> Administrator zamykal zgloszenie
     * z decyzja „usun post" i dostawal blad 500:</p>
     *
     * <pre>
     * update or delete on table "posts" violates foreign key constraint on table "reports"
     * </pre>
     *
     * <p>Powod jest oczywisty, gdy sie go zobaczy: zgloszenie wskazuje na
     * post kluczem obcym, wiec dopoki wskazuje, baza posta nie odda. Ten sam
     * problem co przy kasowaniu konta - tylko dla pojedynczego posta.</p>
     *
     * <p><b>Dlaczego test jednostkowy tego nie zlapal.</b> Atrapa repozytorium
     * nie ma kluczy obcych i pozwoli skasowac cokolwiek. Wiezy spojnosci
     * istnieja wylacznie w prawdziwej bazie i tylko tam da sie je zlamac.</p>
     *
     * <p><b>Odpinamy, zamiast kasowac zgloszenie</b> - i to jest sedno.
     * Zgloszenie jest zapisem tego, ze ktos cos zrobil; skasowanie go razem
     * z postem czyscilo by przy okazji historie konta, na ktorej opieraja sie
     * kolejne decyzje. Tresc posta nie ginie, bo jest w migawce dowodow -
     * dokladnie po to ta migawka powstala.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Report r SET r.post = null WHERE r.post.id = :postId")
    void detachPost(@Param("postId") Long postId);
}
