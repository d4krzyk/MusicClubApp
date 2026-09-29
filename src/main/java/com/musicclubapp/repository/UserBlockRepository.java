package com.musicclubapp.repository;

import com.musicclubapp.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Blokady miedzy uzytkownikami. */
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * Konta, ktorych ta osoba nie ma widziec: te, ktore zablokowala, i te,
     * ktore zablokowaly ja. Jednym zapytaniem - wolane przy kazdej tablicy.
     */
    @Query("""
        SELECT CASE WHEN b.blocker.id = :userId THEN b.blocked.id ELSE b.blocker.id END
          FROM UserBlock b
         WHERE b.blocker.id = :userId OR b.blocked.id = :userId
        """)
    List<Long> hiddenFor(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(b) > 0 FROM UserBlock b
         WHERE (b.blocker.id = :a AND b.blocked.id = :b) OR (b.blocker.id = :b AND b.blocked.id = :a)
        """)
    boolean existsEitherWay(@Param("a") Long a, @Param("b") Long b);

    /** Moja lista zablokowanych - od najnowszej blokady. */
    @Query("SELECT b FROM UserBlock b JOIN FETCH b.blocked WHERE b.blocker.username = :username ORDER BY b.createdAt DESC")
    List<UserBlock> blockedBy(@Param("username") String username);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blocker.id = :userId OR b.blocked.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
