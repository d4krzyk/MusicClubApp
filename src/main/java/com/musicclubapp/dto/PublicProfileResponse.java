package com.musicclubapp.dto;

import java.time.LocalDateTime;

/**
 * Profil uzytkownika widziany przez INNYCH.
 *
 * <p><b>Czego tu celowo nie ma: adresu e-mail i roli.</b> To osobne DTO wlasnie
 * po to - {@code UserResponse} (wlasne konto) zawiera e-mail, bo swoj adres
 * kazdy ma prawo zobaczyc. Gdyby publiczny profil uzywal tej samej klasy,
 * wystarczyloby wejsc na cudzy profil, zeby poznac czyjs adres. O tym,
 * co wychodzi na zewnatrz, decyduje wiec WYBOR KLASY, a nie warunek {@code if}
 * w srodku mapowania - taki warunek latwo przeoczyc przy kolejnej zmianie.</p>
 *
 * @param self       czy to profil zalogowanego uzytkownika - po tym froncie
 *                   decyduje, czy pokazac przycisk "Edytuj profil"
 * @param postCount  ile postow napisal - jedyna liczba, ktora ma sens pokazac,
 *                   zanim dojda znajomi i ulubieni artysci
 * @param createdAt  wymaganie nr 4 (data i czas) - pokazujemy jako "czlonek od..."
 */
public record PublicProfileResponse(
    String username,
    String avatarUrl,
    LocalDateTime createdAt,
    long postCount,
    boolean self,
    /** Ilu ma znajomych - liczba nad paskiem znajomych. */
    long friendCount,
    /**
     * W jakiej relacji jest z nim ogladajacy.
     *
     * <p>Po tym polu frontend wybiera przycisk: "Zapros", "Przyjmij
     * zaproszenie", "Usun ze znajomych" albo brak. Wylicza je SERWER -
     * gdyby decydowal o tym warunek w przegladarce, wystarczyloby go obejsc
     * narzedziami deweloperskimi. Backend i tak sprawdza uprawnienia
     * ponownie przy kazdej operacji.</p>
     */
    FriendshipStatus friendshipStatus
) {
}
