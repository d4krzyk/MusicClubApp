package com.musicclubapp.repository;

import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventSourceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventSourceEntryRepository extends JpaRepository<EventSourceEntry, Long> {

    Optional<EventSourceEntry> findBySourceAndExternalId(EventSource source, String externalId);

    /** Wpisy zrodel pobocznych dla tych wydarzen - do uzupelnienia brakow po imporcie Ticketmastera. */
    @Query("SELECT s FROM EventSourceEntry s WHERE s.event.id IN :ids ORDER BY s.source, s.id")
    List<EventSourceEntry> ofEvents(@Param("ids") Collection<Long> ids);

    /** Wpisy, ktorych zrodlo dawno juz nie pokazuje - nie uzupelniaja niczego. */
    @Modifying
    @Query("DELETE FROM EventSourceEntry s WHERE s.source = :source AND s.seenAt < :before")
    int deleteStale(@Param("source") EventSource source, @Param("before") LocalDateTime before);
}
