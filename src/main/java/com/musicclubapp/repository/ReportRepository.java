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

    /** Zgloszenia zlozone przez jedna osobe - do jej wlasnej listy. */
    Page<Report> findByReporterUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /** Liczba czekajacych - to ona wisi przy ikonie panelu. */
    long countByStatus(ReportStatus status);

    /** Czy zglaszajacy ma juz OTWARTE zgloszenie na te osobe. */
    boolean existsByReporterIdAndReportedIdAndStatus(Long reporterId, Long reportedId,
                                                     ReportStatus status);

    /** Ile zgloszen ta osoba wyslala od podanej chwili. */
    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime since);

    /** Ile razy ta osoba byla zglaszana i uznana za winna. */
    long countByReportedIdAndStatus(Long reportedId, ReportStatus status);

    /** Ile zasadnych zgloszen ma KAZDE ze wskazanych kont - jednym zapytaniem. */
    @Query("""
           SELECT new com.musicclubapp.repository.CountByUser(r.reported.id, COUNT(r))
           FROM Report r
           WHERE r.status = :status AND r.reported.id IN :userIds
           GROUP BY r.reported.id
           """)
    List<CountByUser> countByStatusForUsers(@Param("status") ReportStatus status,
                                            @Param("userIds") List<Long> userIds);

    /** Kasuje zgloszenia zwiazane z kontem - w obie strony. */
    @Modifying
    @Query("DELETE FROM Report r WHERE r.reporter.id = :userId OR r.reported.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);

    /** Odpina posty od zgloszen przed skasowaniem tych postow. */
    @Modifying
    @Query("UPDATE Report r SET r.post = null WHERE r.post.author.id = :authorId")
    void detachPostsOfAuthor(@Param("authorId") Long authorId);

    /** Odpina JEDEN post od wszystkich zgloszen, ktore go wskazuja. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Report r SET r.post = null WHERE r.post.id = :postId")
    void detachPost(@Param("postId") Long postId);
}
