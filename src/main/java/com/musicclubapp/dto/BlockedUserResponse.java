package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Pozycja na mojej liscie zablokowanych. */
public record BlockedUserResponse(String username, String avatarUrl, LocalDateTime blockedAt) {
}
