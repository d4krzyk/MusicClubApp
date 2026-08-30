package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Inne konto logujace sie z tego samego adresu sieciowego. */
public record RelatedAccountResponse(
    Long userId,
    String username,
    String avatarUrl,
    String address,
    LocalDateTime lastSeenAt,
    int loginCount
) {
}
