package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Zablokowany adres na liscie w panelu administratora. */
public record BlockedIpResponse(
    Long id,
    String address,
    String reason,
    String blockedBy,
    LocalDateTime createdAt
) {
}
