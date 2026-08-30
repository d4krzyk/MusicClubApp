package com.musicclubapp.dto;

/** Jedna pozycja na liscie rozmow w oknie czatu. */
public record ConversationResponse(
    String username,
    String avatarUrl,
    PresenceResponse presence,
    MessageResponse lastMessage,
    long unread,
    boolean friend
) {
}
