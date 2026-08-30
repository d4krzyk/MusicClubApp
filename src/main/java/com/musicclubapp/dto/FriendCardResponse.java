package com.musicclubapp.dto;

/** Kafelek znajomego na pasku pod profilem. */
public record FriendCardResponse(
    String username,
    String avatarUrl,
    long mutualFriends,
    /** Kropka "online" i podpowiedz "aktywny 5 minut temu" na kafelku. */
    PresenceResponse presence
) {
}
