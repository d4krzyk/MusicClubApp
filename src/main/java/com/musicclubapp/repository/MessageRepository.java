package com.musicclubapp.repository;

import com.musicclubapp.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Zapytania o wiadomosci. */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Historia rozmowy dwoch osob, od najnowszej. */
    @Query("""
           SELECT m FROM Message m
           WHERE ((m.sender.id = :first  AND m.recipient.id = :second)
               OR (m.sender.id = :second AND m.recipient.id = :first))
             AND ((m.sender.id = :first AND m.hiddenForSender = false)
               OR (m.recipient.id = :first AND m.hiddenForRecipient = false))
           ORDER BY m.id DESC
           """)
    Page<Message> conversation(@Param("first") Long first,
                               @Param("second") Long second,
                               Pageable pageable);

    /** Wiadomosci z rozmowy nowsze niz ta, ktora juz mamy. */
    @Query("""
           SELECT m FROM Message m
           WHERE ((m.sender.id = :first  AND m.recipient.id = :second)
               OR (m.sender.id = :second AND m.recipient.id = :first))
             AND m.id > :afterId
             AND ((m.sender.id = :first AND m.hiddenForSender = false)
               OR (m.recipient.id = :first AND m.hiddenForRecipient = false))
           ORDER BY m.id ASC
           """)
    List<Message> newerThan(@Param("first") Long first,
                            @Param("second") Long second,
                            @Param("afterId") Long afterId,
                            Pageable limit);

    /** Ostatnia wiadomosc kazdej rozmowy - z kim i ktora. */
    @Query("""
           SELECT new com.musicclubapp.repository.ConversationRow(m.recipient.id, MAX(m.id))
           FROM Message m
           WHERE m.sender.id = :me AND m.hiddenForSender = false
           GROUP BY m.recipient.id
           """)
    List<ConversationRow> lastSentPerPartner(@Param("me") Long me);

    /** Ostatnia wiadomosc OD kazdej osoby, ktora do nas napisala. */
    @Query("""
           SELECT new com.musicclubapp.repository.ConversationRow(m.sender.id, MAX(m.id))
           FROM Message m
           WHERE m.recipient.id = :me AND m.hiddenForRecipient = false
           GROUP BY m.sender.id
           """)
    List<ConversationRow> lastReceivedPerPartner(@Param("me") Long me);

    /** Ostatnia wiadomosc w kazdej rozmowie - niezaleznie od tego, kto ja wyslal. */
    /** Czy te dwie osoby kiedykolwiek cos do siebie napisaly. */
    @Query("""
           SELECT COUNT(m) > 0 FROM Message m
           WHERE ((m.sender.id = :a AND m.recipient.id = :b)
               OR (m.sender.id = :b AND m.recipient.id = :a))
             AND ((m.sender.id = :a AND m.hiddenForSender = false)
               OR (m.recipient.id = :a AND m.hiddenForRecipient = false))
           """)
    boolean anyMessageBetween(@Param("a") Long a, @Param("b") Long b);

    default List<ConversationRow> lastMessagePerConversation(Long me) {
        Map<Long, Long> newest = new LinkedHashMap<>();
        for (ConversationRow row : lastSentPerPartner(me)) {
            newest.merge(row.partnerId(), row.lastMessageId(), Math::max);
        }
        for (ConversationRow row : lastReceivedPerPartner(me)) {
            newest.merge(row.partnerId(), row.lastMessageId(), Math::max);
        }
        return newest.entrySet().stream()
            .map(entry -> new ConversationRow(entry.getKey(), entry.getValue()))
            .toList();
    }

    /** Ile nieprzeczytanych czeka od kazdej osoby z osobna - jednym zapytaniem. */
    @Query("""
           SELECT new com.musicclubapp.repository.UnreadRow(m.sender.id, COUNT(m))
           FROM Message m
           WHERE m.recipient.id = :me AND m.readAt IS NULL
             AND m.hiddenForRecipient = false
           GROUP BY m.sender.id
           """)
    List<UnreadRow> unreadBySender(@Param("me") Long me);

    /** Najwyzszy identyfikator MOJEJ wiadomosci, ktora rozmowca juz przeczytal. */
    @Query("""
           SELECT MAX(m.id) FROM Message m
           WHERE m.sender.id = :me
             AND m.recipient.id = :partner
             AND m.readAt IS NOT NULL
             AND m.hiddenForSender = false
           """)
    Long lastReadOutgoingId(@Param("me") Long me, @Param("partner") Long partner);

    /** Laczna liczba nieprzeczytanych - to ona wisi przy ikonie czatu. */
    @Query("""
           SELECT COUNT(m) FROM Message m
           WHERE m.recipient.id = :me AND m.readAt IS NULL
             AND m.hiddenForRecipient = false
           """)
    long countUnread(@Param("me") Long me);

    /** Oznacza cala rozmowe jako przeczytana. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Message m SET m.readAt = :now
           WHERE m.recipient.id = :me
             AND m.sender.id = :partner
             AND m.readAt IS NULL
             AND m.hiddenForRecipient = false
           """)
    int markConversationRead(@Param("me") Long me,
                             @Param("partner") Long partner,
                             @Param("now") LocalDateTime now);

    /** Kasuje wszystkie wiadomosci danego konta - w obie strony. */
    @Modifying
    @Query("DELETE FROM Message m WHERE m.sender.id = :userId OR m.recipient.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);

    /* ------------------------------------------------------------------ */
    /*  Usuwanie rozmowy u jednej strony                                   */
    /* ------------------------------------------------------------------ */

    /** Ukrywa u nadawcy wiadomosci, ktore sam wyslal do tej osoby. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Message m SET m.hiddenForSender = true
           WHERE m.sender.id = :me AND m.recipient.id = :partner
           """)
    int hideSentTo(@Param("me") Long me, @Param("partner") Long partner);

    /** Ukrywa u odbiorcy wiadomosci, ktore od tej osoby dostal. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Message m SET m.hiddenForRecipient = true
           WHERE m.recipient.id = :me AND m.sender.id = :partner
           """)
    int hideReceivedFrom(@Param("me") Long me, @Param("partner") Long partner);

    /** Wiadomosci ukryte przez OBIE strony - nikt ich juz nie zobaczy. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           DELETE FROM Message m
           WHERE m.hiddenForSender = true AND m.hiddenForRecipient = true
           """)
    int deleteHiddenByBothSides();

    /**
     * Wszystkie wiadomosci tej osoby - wyslane i otrzymane - bez tych z rozmow, ktore skasowala
     * u siebie (znaczniki hidden): kto skasowal rozmowe, nie chce jej dostac w pobranych danych.
     */
    @Query("""
           SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.recipient
           WHERE (m.sender.id = :userId AND m.hiddenForSender = false)
              OR (m.recipient.id = :userId AND m.hiddenForRecipient = false)
           ORDER BY m.id
           """)
    List<Message> ofUser(@Param("userId") Long userId);
}
