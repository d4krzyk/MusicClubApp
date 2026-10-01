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
    String pendingEmail,
    /** Przy zmianie adresu: czy stara skrzynka juz sie zgodzila. */
    boolean pendingEmailOldApproved,
    /** Przy zmianie adresu: czy nowy adres juz potwierdzony. */
    boolean pendingEmailNewVerified,
    /** Miasto z profilu albo {@code null}. */
    String city,
    /** Czy znamy wspolrzedne tego miasta - bez nich nie ma "w promieniu X km", jest tylko "to samo miasto". */
    boolean cityLocated
) {
}
