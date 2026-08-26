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

    /**
     * Zaproszenia PRZYCHODZACE - te, na ktore mam odpowiedziec.
     *
     * <p>{@code JOIN FETCH} dociaga nadawce od razu, bo i tak pokazujemy jego
     * nazwe i awatar. Bez tego kazde zaproszenie na liscie oznaczaloby osobne
     * zapytanie o autora (problem N+1).</p>
     */
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

    /**
     * Konkretne zaproszenie miedzy dwiema osobami - w PODANYM kierunku.
     *
     * <p>Kierunek ma znaczenie: {@code znajdz(a, b)} to "czy A zaprosil B",
     * a {@code znajdz(b, a)} to pytanie odwrotne. Serwis uzywa obu - drugie
     * po to, zeby wykryc, ze obie osoby zaprosily sie nawzajem.</p>
     */
    @Query("""
           SELECT z FROM FriendRequest z
           WHERE z.sender.username = :nadawca AND z.recipient.username = :odbiorca
           """)
    Optional<FriendRequest> find(@Param("nadawca") String sender,
                                   @Param("odbiorca") String recipient);

    /** Ile zaproszen czeka na moja odpowiedz - liczba przy pozycji w menu. */
    long countByRecipientUsername(String username);

    /**
     * Kasuje zaproszenia, w ktorych dana osoba wystepuje po DOWOLNEJ stronie.
     *
     * <p>Oba warunki sa konieczne: zaproszenie wyslane i zaproszenie
     * otrzymane to dwa rozne wiersze, a oba wskazuja na to samo konto.</p>
     */
    @Modifying
    @Query("DELETE FROM FriendRequest f WHERE f.sender.id = :senderId OR f.recipient.id = :recipientId")
    void deleteBySenderIdOrRecipientId(@Param("senderId") Long senderId,
                                       @Param("recipientId") Long recipientId);
}
