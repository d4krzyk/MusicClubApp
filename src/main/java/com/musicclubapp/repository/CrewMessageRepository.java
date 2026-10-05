package com.musicclubapp.repository;

import com.musicclubapp.entity.CrewMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CrewMessageRepository extends JpaRepository<CrewMessage, Long> {

    @Query("""
        SELECT m FROM CrewMessage m JOIN FETCH m.sender
         WHERE m.crew.id = :crewId AND m.sender.id NOT IN :hidden ORDER BY m.id DESC
        """)
    List<CrewMessage> latest(@Param("crewId") Long crewId, @Param("hidden") Collection<Long> hidden, Pageable page);

    @Query("""
        SELECT m FROM CrewMessage m JOIN FETCH m.sender
         WHERE m.crew.id = :crewId AND m.id > :after AND m.sender.id NOT IN :hidden ORDER BY m.id
        """)
    List<CrewMessage> after(@Param("crewId") Long crewId, @Param("after") Long after,
                            @Param("hidden") Collection<Long> hidden, Pageable page);

    @Query("""
        SELECT m FROM CrewMessage m JOIN FETCH m.sender
         WHERE m.crew.id = :crewId AND m.id < :before AND m.sender.id NOT IN :hidden ORDER BY m.id DESC
        """)
    List<CrewMessage> before(@Param("crewId") Long crewId, @Param("before") Long before,
                             @Param("hidden") Collection<Long> hidden, Pageable page);

    @Query("SELECT m.id FROM CrewMessage m WHERE m.crew.id = :crewId AND m.deletedAt >= :since")
    List<Long> deletedSince(@Param("crewId") Long crewId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(MAX(m.id), 0) FROM CrewMessage m WHERE m.crew.id = :crewId")
    long maxId(@Param("crewId") Long crewId);

    /** Nieprzeczytane: wiadomosci innych osob nowsze niz znacznik, bez usunietych i osob z blokad. */
    @Query("""
        SELECT COUNT(m) FROM CrewMessage m
         WHERE m.crew.id = :crewId AND m.id > :readId AND m.sender.id <> :userId
           AND m.deletedAt IS NULL AND m.sender.id NOT IN :hidden
        """)
    long unread(@Param("crewId") Long crewId, @Param("userId") Long userId, @Param("readId") long readId,
                @Param("hidden") Collection<Long> hidden);

    @Query("SELECT m FROM CrewMessage m JOIN FETCH m.crew WHERE m.sender.id = :userId AND m.deletedAt IS NULL ORDER BY m.id")
    List<CrewMessage> writtenBy(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM CrewMessage m WHERE m.sender.id = :userId")
    int deleteBySenderId(@Param("userId") Long userId);
}
