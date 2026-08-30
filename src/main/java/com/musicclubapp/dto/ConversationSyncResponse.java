package com.musicclubapp.dto;

import java.util.List;

/** Odpowiedz na pytanie "co nowego w tej rozmowie". */
public record ConversationSyncResponse(
    List<MessageResponse> messages,
    boolean partnerTyping,
    PresenceResponse presence,
    long unread,
    Long lastReadOutgoingId,
    boolean friend
) {
}
