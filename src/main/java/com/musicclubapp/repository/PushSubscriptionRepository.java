package com.musicclubapp.repository;

import com.musicclubapp.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdOrderByCreatedAtAsc(Long userId);

    /**
     * Urzadzenia, ktore dalej maja dostawac powiadomienia: zapisane przy obecnym
     * znaczniku konta. COALESCE - konta sprzed znacznikow maja NULL, a NULL = ''
     * nie jest w SQL prawda.
     */
    @Query("SELECT s FROM PushSubscription s WHERE s.user.id = :userId AND s.stamp = COALESCE(s.user.securityStamp, '')")
    List<PushSubscription> active(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM PushSubscription s WHERE s.user.id = :userId AND s.stamp = COALESCE(s.user.securityStamp, '')")
    long countActive(@Param("userId") Long userId);

    /** Po zmianie znacznika - zapisane wczesniej urzadzenia juz nic nie dostana. */
    @Transactional
    @Modifying
    @Query("""
           DELETE FROM PushSubscription s
           WHERE s.user.id = :userId
             AND s.stamp <> (SELECT COALESCE(u.securityStamp, '') FROM User u WHERE u.id = :userId)
           """)
    int deleteStale(@Param("userId") Long userId);

    /** Usluga push odpowiedziala, ze takiego adresu juz nie ma (404/410). */
    @Transactional
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint")
    int deleteByEndpoint(@Param("endpoint") String endpoint);

    @Transactional
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
