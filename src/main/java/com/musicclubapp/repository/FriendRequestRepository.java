package com.musicclubapp.repository;

import com.musicclubapp.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Zaproszenia do znajomych, ktore czekaja na odpowiedz. */
@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /** Zaproszenia PRZYCHODZACE - te, na ktore mam odpowiedziec. */
    @Query("""
           SELECT z FROM FriendRequest z
           JOIN FETCH z.sender
           WHERE z.recipient.username = :username
           ORDER BY z.createdAt DESC
           """)
    List<FriendRequest> incoming(@Param("username") String username);

    /** Zaproszenia WYSLANE - czekaja na cudza decyzje, mozna je anulowac. */
    @Query("""
           SELECT z FROM FriendRequest z
           JOIN FETCH z.recipient
           WHERE z.sender.username = :username
           ORDER BY z.createdAt DESC
           """)
    List<FriendRequest> outgoing(@Param("username") String username);

    /** Konkretne zaproszenie miedzy dwiema osobami - w PODANYM kierunku. */
    @Query("""
           SELECT z FROM FriendRequest z
           WHERE z.sender.username = :nadawca AND z.recipient.username = :odbiorca
           """)
    Optional<FriendRequest> find(@Param("nadawca") String sender,
                                   @Param("odbiorca") String recipient);

    /** Ile zaproszen czeka na moja odpowiedz - liczba przy pozycji w menu. */
    long countByRecipientUsername(String username);

    /** Kasuje zaproszenia, w ktorych dana osoba wystepuje po DOWOLNEJ stronie. */
    @Modifying
    @Query("DELETE FROM FriendRequest f WHERE f.sender.id = :senderId OR f.recipient.id = :recipientId")
    void deleteBySenderIdOrRecipientId(@Param("senderId") Long senderId,
                                       @Param("recipientId") Long recipientId);
}
