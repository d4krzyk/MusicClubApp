package com.musicclubapp.repository;

import com.musicclubapp.entity.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Linki potwierdzajace adres e-mail. */
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    @Query("SELECT t FROM EmailToken t JOIN FETCH t.user WHERE t.tokenHash = :hash")
    Optional<EmailToken> findByHash(@Param("hash") String tokenHash);

    /** Ile wiadomosci poszlo do tej osoby od podanej chwili - do limitu wysylek. */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Kiedy poszla ostatnia - zeby "wyslij ponownie" nie dalo sie klikac co sekunde. */
    Optional<EmailToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** Najstarsza z ostatniej doby - od niej liczy sie, kiedy limit sie zwolni. */
    Optional<EmailToken> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, LocalDateTime since);

    /** Linki na jeden adres - po jego potwierdzeniu albo rezygnacji ze zmiany. */
    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.user.id = :userId AND t.email = :email")
    int deleteByUserIdAndEmail(@Param("userId") Long userId, @Param("email") String email);

    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);

    /** Przeterminowane - juz niczego nie potwierdza, a limit wysylek liczy tylko ostatnia dobe. */
    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
