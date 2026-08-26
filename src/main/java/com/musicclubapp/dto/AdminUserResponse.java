package com.musicclubapp.dto;

import com.musicclubapp.entity.Role;

import java.time.LocalDateTime;

/**
 * Dane uzytkownika widziane przez ADMINISTRATORA.
 *
 * <p><b>Czym rozni sie od {@link UserResponse}?</b> Zawiera pole {@code role}.
 * Zwykly uzytkownik nigdy go nie dostaje - jego wlasny profil chodzi przez
 * {@code UserResponse}, gdzie roli nie ma w ogole. Administrator musi ja
 * widziec, bo inaczej nie mialby czego zmieniac w panelu.</p>
 *
 * <p>Dwa osobne DTO zamiast jednego z opcjonalnym polem sa tu celowe:
 * o tym, ktore dane wychodza na zewnatrz, decyduje wybor klasy w kontrolerze,
 * a nie warunek {@code if} gdzies w srodku mapowania. Latwiej to sprawdzic
 * i trudniej przypadkiem ujawnic za duzo.</p>
 */
public record AdminUserResponse(
    Long id,
    String username,
    String email,
    Role role,
    LocalDateTime createdAt,

    /**
     * Do kiedy obowiazuje zakaz publikowania; {@code null} - gdy zadnego nie ma.
     *
     * <p>Oddajemy sam termin, a nie gotowe "zablokowany: tak/nie". Panel
     * pokazuje dzieki temu, do kiedy kara trwa, a data w przeszlosci mowi
     * administratorowi, ze ktos byl juz kiedys blokowany - i to bywa
     * wazniejsze niz sam biezacy stan.</p>
     */
    LocalDateTime postingBannedUntil
) {
}
