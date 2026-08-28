package com.musicclubapp.dto;

import java.time.LocalDateTime;

/**
 * Czy ktos jest teraz aktywny i kiedy byl ostatnio.
 *
 * <p><b>Wysylamy OBIE wartosci, choc jedna wynika z drugiej.</b> Przegladarka
 * moglaby sobie wyliczyc {@code online} z samej daty - i wlasnie dlatego tego
 * nie robimy. Wynik zalezalby wtedy od zegara komputera uzytkownika, ktory
 * bywa przestawiony o godziny, a granica "ile minut to jeszcze online" bylaby
 * zapisana w dwoch miejscach naraz. Regula jest jedna i stoi na serwerze;
 * data sluzy juz tylko do napisania "aktywny 5 minut temu".</p>
 *
 * @param online     czy byl aktywny w ostatnich kilku minutach
 * @param lastSeenAt kiedy dokladnie; {@code null} = nigdy nie byl widziany
 */
public record PresenceResponse(
    boolean online,
    LocalDateTime lastSeenAt
) {
}
