package com.musicclubapp.repository;

import com.musicclubapp.entity.EmailToken;
import com.musicclubapp.entity.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Linki z wiadomosci: potwierdzenie adresu, zgoda na zmiane, reset hasla. */
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    @Query("SELECT t FROM EmailToken t JOIN FETCH t.user WHERE t.tokenHash = :hash")
    Optional<EmailToken> findByHash(@Param("hash") String tokenHash);

    /** Ile wiadomosci danego rodzaju poszlo do tej osoby od podanej chwili - do limitu wysylek. */
    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, TokenPurpose purpose, LocalDateTime since);

    /** Kiedy poszla ostatnia - zeby "wyslij ponownie" nie dalo sie klikac co sekunde. */
    Optional<EmailToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, TokenPurpose purpose);

    /** Najstarsza z ostatniej doby - od niej liczy sie, kiedy limit sie zwolni. */
    Optional<EmailToken> findFirstByUserIdAndPurposeAndCreatedAtAfterOrderByCreatedAtAsc(
        Long userId, TokenPurpose purpose, LocalDateTime since);

    /*
     * Uzyte i porzucone linki WYGASZAMY, a nie kasujemy. Limit wysylek liczy
     * linki z ostatniej doby - skasowany link znikalby z licznika, i petla
     * "zmien adres -> anuluj" pozwalalaby wysylac wiadomosci bez konca.
     */

    /** Linki na jeden adres - po jego potwierdzeniu albo rezygnacji ze zmiany. */
    @Modifying
    @Query("""
        UPDATE EmailToken t SET t.expiresAt = :now
         WHERE t.user.id = :userId AND t.email = :email AND t.expiresAt > :now
        """)
    int expireByUserIdAndEmail(@Param("userId") Long userId, @Param("email") String email,
                               @Param("now") LocalDateTime now);

    /** Linki jednego rodzaju - np. wszystkie resety hasla po jego ustawieniu. */
    @Modifying
    @Query("""
        UPDATE EmailToken t SET t.expiresAt = :now
         WHERE t.user.id = :userId AND t.purpose = :purpose AND t.expiresAt > :now
        """)
    int expireByUserIdAndPurpose(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                                 @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE EmailToken t SET t.expiresAt = :now WHERE t.user.id = :userId AND t.expiresAt > :now")
    int expireAllOfUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);

    /**
     * Starsze niz doba - i tak juz niewazne (zaden link nie dziala dluzej),
     * a limit wysylek patrzy tylko na ostatnia dobe.
     */
    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
