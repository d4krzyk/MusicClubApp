package com.musicclubapp.dto;

import java.time.LocalDateTime;

/**
 * Inne konto logujace sie z tego samego adresu sieciowego.
 *
 * <p><b>To jest poszlaka, nie dowod</b>, i interfejs mowi to wprost. Pod
 * jednym adresem siedzi cala rodzina, akademik albo tysiace klientow
 * operatora komorkowego. Dlatego oddajemy takze {@code loginCount}
 * i {@code lastSeenAt}: dwa konta z jednym wejsciem sprzed pol roku znacza
 * co innego niz dwa konta uzywane naprzemiennie codziennie.</p>
 *
 * @param address adres, ktory te konta laczy - administrator moze go
 *                skopiowac wprost do blokady
 */
public record RelatedAccountResponse(
    Long userId,
    String username,
    String avatarUrl,
    String address,
    LocalDateTime lastSeenAt,
    int loginCount
) {
}
