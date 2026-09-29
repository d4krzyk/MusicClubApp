package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Dane uzytkownika wysylane do klienta. */
public record UserResponse(
    Long id,
    String username,
    String email,
    boolean admin,
    /** Gotowy adres zdjecia profilowego albo {@code null}, gdy uzytkownik go nie wgral. */
    String avatarUrl,
    LocalDateTime createdAt,
    /** Czy adres jest potwierdzony linkiem z wiadomosci. */
    boolean emailVerified,
    /** Nowy adres czekajacy na klikniecie w link - albo {@code null}. */
    String pendingEmail
) {
}
