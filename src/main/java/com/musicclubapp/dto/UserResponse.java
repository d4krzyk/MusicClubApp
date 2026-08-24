package com.musicclubapp.dto;

import com.musicclubapp.entity.Role;

import java.time.LocalDateTime;

/**
 * Dane uzytkownika wysylane do klienta.
 *
 * <p>Osobne DTO na odpowiedz jest wazne ze wzgledow bezpieczenstwa: encja
 * {@code User} trzyma {@code passwordHash}, a ten nie ma prawa opuscic
 * serwera. Zwracajac DTO mamy pewnosc, ze wysylamy dokladnie to, co chcemy -
 * zamiast liczyc na to, ze ktos pamietal o {@code @JsonIgnore} na encji.</p>
 *
 * <p>{@code createdAt} (wymaganie nr 4) pokazujemy jako "czlonek od...".</p>
 */
public record UserResponse(
    Long id,
    String username,
    String email,
    Role role,
    LocalDateTime createdAt
) {
}
