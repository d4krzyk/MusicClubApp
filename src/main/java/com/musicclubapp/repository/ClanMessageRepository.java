package com.musicclubapp.repository;

import com.musicclubapp.entity.ClanMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClanMessageRepository extends JpaRepository<ClanMessage, Long> {

    /** Najnowsze wiadomosci (od najnowszej); bez osob z blokad ogladajacego. */
    @Query("""
           SELECT m FROM ClanMessage m JOIN FETCH m.sender
           LEFT JOIN FETCH m.replyTo rt LEFT JOIN FETCH rt.sender
           WHERE m.clan.id = :clanId AND m.sender.id NOT IN :hidden
           ORDER BY m.id DESC
           """)
    List<ClanMessage> latest(@Param("clanId") Long clanId, @Param("hidden") Collection<Long> hidden, Pageable limit);

    /** Starsze niz podana (od najnowszej) - "pokaz wczesniejsze". */
    @Query("""
           SELECT m FROM ClanMessage m JOIN FETCH m.sender
           LEFT JOIN FETCH m.replyTo rt LEFT JOIN FETCH rt.sender
           WHERE m.clan.id = :clanId AND m.id < :before AND m.sender.id NOT IN :hidden
           ORDER BY m.id DESC
           """)
    List<ClanMessage> before(@Param("clanId") Long clanId, @Param("before") Long before,
                             @Param("hidden") Collection<Long> hidden, Pageable limit);

    /** Nowsze niz podana (od najstarszej) - odswiezanie czatu. */
    @Query("""
           SELECT m FROM ClanMessage m JOIN FETCH m.sender
           LEFT JOIN FETCH m.replyTo rt LEFT JOIN FETCH rt.sender
           WHERE m.clan.id = :clanId AND m.id > :after AND m.sender.id NOT IN :hidden
           ORDER BY m.id ASC
           """)
    List<ClanMessage> after(@Param("clanId") Long clanId, @Param("after") Long after,
                            @Param("hidden") Collection<Long> hidden, Pageable limit);

    /** Numer najnowszej wiadomosci klanu (0, gdy czat jest pusty). */
    @Query("SELECT COALESCE(MAX(m.id), 0) FROM ClanMessage m WHERE m.clan.id = :clanId")
    long maxId(@Param("clanId") Long clanId);

    /** Ile wiadomosci od innych osob jest nowszych niz ostatnia przeczytana - bez osob z blokad. */
    @Query("""
           SELECT COUNT(m) FROM ClanMessage m
           WHERE m.clan.id = :clanId AND m.id > :readId AND m.sender.id <> :userId AND m.sender.id NOT IN :hidden
           """)
    long unread(@Param("clanId") Long clanId, @Param("readId") long readId, @Param("userId") Long userId,
                @Param("hidden") Collection<Long> hidden);

    /** Ile wiadomosci napisano w tym klanie od podanej chwili. */
    @Query("SELECT COUNT(m) FROM ClanMessage m WHERE m.clan.id = :clanId AND m.createdAt >= :since")
    long countSince(@Param("clanId") Long clanId, @Param("since") java.time.LocalDateTime since);

    /** Ile wiadomosci napisano w kazdym klanie od podanej chwili - poziom aktywnosci w przegladarce klanow. */
    @Query("SELECT m.clan.id AS clanId, COUNT(m) AS total FROM ClanMessage m WHERE m.createdAt >= :since GROUP BY m.clan.id")
    List<ClanCountRow> countsSince(@Param("since") java.time.LocalDateTime since);

    @Modifying
    @Query("DELETE FROM ClanMessage m WHERE m.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    @Modifying
    @Query("DELETE FROM ClanMessage m WHERE m.sender.id = :userId")
    void deleteBySenderId(@Param("userId") Long userId);

    /** Wiadomosci na czatach klanow napisane przez te osobe. */
    @Query("SELECT m FROM ClanMessage m JOIN FETCH m.clan WHERE m.sender.id = :userId ORDER BY m.id")
    List<ClanMessage> writtenBy(@Param("userId") Long userId);
}
