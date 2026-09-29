package com.musicclubapp.repository;

import com.musicclubapp.entity.Notification;
import com.musicclubapp.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Dostep do powiadomien. */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Powiadomienia jednej osoby, od najnowszych. */
    @Query(value = """
           SELECT n FROM Notification n
           JOIN FETCH n.actor
           LEFT JOIN FETCH n.post
           WHERE n.recipient.username = :username
           ORDER BY n.createdAt DESC
           """,
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient.username = :username")
    Page<Notification> forUser(@Param("username") String username, Pageable pageable);

    /** Ile nieprzeczytanych - to liczba przy dzwonku. */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.username = :username AND n.readAt IS NULL")
    long countUnread(@Param("username") String username);

    /** Istniejace powiadomienie o tej samej rzeczy. */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.recipient.id = :recipientId
             AND n.actor.id = :actorId
             AND n.post.id = :postId
             AND n.type = :type
           """)
    Optional<Notification> find(@Param("recipientId") Long recipientId,
                                @Param("actorId") Long actorId,
                                @Param("postId") Long postId,
                                @Param("type") NotificationType type);

    /** Kasuje powiadomienie o reakcji, ktora zostala cofnieta. */
    @Modifying
    @Query("""
           DELETE FROM Notification n
           WHERE n.recipient.id = :recipientId
             AND n.actor.id = :actorId
             AND n.post.id = :postId
             AND n.type = :type
           """)
    void deleteMatching(@Param("recipientId") Long recipientId,
                        @Param("actorId") Long actorId,
                        @Param("postId") Long postId,
                        @Param("type") NotificationType type);

    /**
     * Kasuje powiadomienia o zaproszeniu, ktore przestalo istniec (zostalo odrzucone albo
     * anulowane).
     */
    @Modifying
    @Query("""
           DELETE FROM Notification n
           WHERE n.recipient.id = :recipientId
             AND n.actor.id = :actorId
             AND n.type = :type
           """)
    void deleteByType(@Param("recipientId") Long recipientId,
                      @Param("actorId") Long actorId,
                      @Param("type") NotificationType type);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP "
         + "WHERE n.recipient.username = :username AND n.readAt IS NULL")
    int markAllRead(@Param("username") String username);

    /** Wszystkie powiadomienia o danym poscie - przed jego usunieciem. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    /** Powiadomienia zwiazane z kontem - w OBIE strony. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :userId OR n.actor.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** Po blokadzie: powiadomienia miedzy tymi dwiema osobami - w obie strony. */
    @Modifying
    @Query("""
        DELETE FROM Notification n
         WHERE (n.recipient.id = :a AND n.actor.id = :b) OR (n.recipient.id = :b AND n.actor.id = :a)
        """)
    void deleteBetween(@Param("a") Long a, @Param("b") Long b);

    /** Powiadomienia dotyczace postow jednego autora - przed skasowaniem tych postow. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.post.author.id = :authorId")
    void deleteByPostAuthorId(@Param("authorId") Long authorId);
}
