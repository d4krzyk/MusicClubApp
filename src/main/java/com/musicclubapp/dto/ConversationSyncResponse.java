package com.musicclubapp.dto;

import java.util.List;

/** Odpowiedz na pytanie "co nowego w tej rozmowie". */
public record ConversationSyncResponse(
    List<MessageResponse> messages,
    boolean partnerTyping,
    PresenceResponse presence,
    long unread,
    Long lastReadOutgoingId,
    boolean friend,
    /** Wiadomosci tej rozmowy usuniete od czasu podanego w zapytaniu - przegladarka podmienia je na "usunieta". */
    List<Long> deletedIds,
    /** Czas serwera - przegladarka odsyla go przy nastepnym odpytaniu jako "zmiany od". */
    java.time.LocalDateTime serverTime,
    /** Spotkania tej rozmowy zmienione od czasu podanego w zapytaniu (odpowiedzi, odwolanie) - w nowym stanie. */
    List<MeetingResponse> meetings
) {
}
