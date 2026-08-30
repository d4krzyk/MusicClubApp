package com.musicclubapp.dto;

import com.musicclubapp.entity.Role;

import java.time.LocalDateTime;

/** Dane uzytkownika widziane przez ADMINISTRATORA. */
public record AdminUserResponse(
    Long id,
    String username,
    String email,
    Role role,
    LocalDateTime createdAt,

    /** Do kiedy obowiazuje zakaz publikowania; null - gdy zadnego nie ma. */
    LocalDateTime postingBannedUntil,

    /** Do kiedy obowiazuje zakaz wysylania wiadomosci; null - gdy zadnego nie ma. */
    LocalDateTime messagingBannedUntil,

    /** Ile zgloszen na to konto uznano za zasadne. */
    long resolvedReports
) {
}
