package com.musicclubapp.dto;

import java.time.LocalDateTime;

/**
 * Dane uzytkownika wysylane do klienta.
 *
 * <p>Osobne DTO na odpowiedz jest wazne ze wzgledow bezpieczenstwa: encja
 * {@code User} trzyma {@code passwordHash}, a ten nie ma prawa opuscic
 * serwera. Zwracajac DTO mamy pewnosc, ze wysylamy dokladnie to, co chcemy -
 * zamiast liczyc na to, ze ktos pamietal o {@code @JsonIgnore} na encji.</p>
 *
 * <p><b>Dlaczego {@code admin} zamiast pola {@code role}?</b> Zwykly uzytkownik
 * nie powinien w ogole widziec, ze aplikacja ma jakies role - napis "USER"
 * w profilu nic mu nie mowi i tylko zasmieca ekran. Frontend potrzebuje
 * natomiast wiedziec, czy pokazac czesc administracyjna. Jedna flaga
 * {@code true/false} zalatwia to bez ujawniania calego systemu uprawnien.</p>
 *
 * <p>Uwaga: ta flaga sluzy tylko do RYSOWANIA interfejsu. O tym, kto naprawde
 * ma dostep do danych, decyduje wylacznie backend w {@code SecurityConfig} -
 * podmiana tej wartosci w przegladarce niczego nie odblokuje.</p>
 *
 * <p>{@code createdAt} (wymaganie nr 4) pokazujemy jako "czlonek od...".</p>
 */
public record UserResponse(
    Long id,
    String username,
    String email,
    boolean admin,
    LocalDateTime createdAt
) {
}
