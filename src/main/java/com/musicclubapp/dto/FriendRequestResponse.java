package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Zaproszenie do znajomych na liscie oczekujacych. */
public record FriendRequestResponse(
    Long id,
    String username,
    String avatarUrl,
    LocalDateTime createdAt
) {
}
