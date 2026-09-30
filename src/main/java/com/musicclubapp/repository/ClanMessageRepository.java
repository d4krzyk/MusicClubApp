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
           WHERE m.clan.id = :clanId AND m.sender.id NOT IN :hidden
           ORDER BY m.id DESC
           """)
    List<ClanMessage> latest(@Param("clanId") Long clanId, @Param("hidden") Collection<Long> hidden, Pageable limit);

    /** Starsze niz podana (od najnowszej) - "pokaz wczesniejsze". */
    @Query("""
           SELECT m FROM ClanMessage m JOIN FETCH m.sender
           WHERE m.clan.id = :clanId AND m.id < :before AND m.sender.id NOT IN :hidden
           ORDER BY m.id DESC
           """)
    List<ClanMessage> before(@Param("clanId") Long clanId, @Param("before") Long before,
                             @Param("hidden") Collection<Long> hidden, Pageable limit);

    /** Nowsze niz podana (od najstarszej) - odswiezanie czatu. */
    @Query("""
           SELECT m FROM ClanMessage m JOIN FETCH m.sender
           WHERE m.clan.id = :clanId AND m.id > :after AND m.sender.id NOT IN :hidden
           ORDER BY m.id ASC
           """)
    List<ClanMessage> after(@Param("clanId") Long clanId, @Param("after") Long after,
                            @Param("hidden") Collection<Long> hidden, Pageable limit);

    @Modifying
    @Query("DELETE FROM ClanMessage m WHERE m.clan.id = :clanId")
    void deleteByClanId(@Param("clanId") Long clanId);

    @Modifying
    @Query("DELETE FROM ClanMessage m WHERE m.sender.id = :userId")
    void deleteBySenderId(@Param("userId") Long userId);
}
