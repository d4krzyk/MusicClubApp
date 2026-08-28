package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Zakaz publikowania nakladany przez administratora.
 *
 * <p><b>Dlaczego godziny, a nie data konca.</b> Administrator mysli
 * kategoriami "dzien przerwy", a nie "do 14 marca 09:47". Termin wylicza
 * serwer - i wylicza go od <b>swojego</b> zegara, a nie od czasu przyslanego
 * przez klienta. Data z zapytania dalaby sie ustawic na przeszlosc, czyli
 * zamienic kare w nic nieznaczacy wpis.</p>
 *
 * @param hours   ile godzin ma trwac zakaz; {@code null} <b>zdejmuje</b> zakaz
 * @param forever zakaz bezterminowy - wtedy {@code hours} nie ma znaczenia
 */
@Schema(description = "Zakaz publikowania: liczba godzin, zakaz bezterminowy "
    + "albo brak obu wartosci, zeby go zdjac")
public record PostingBanRequest(

    /*
     * Gorne ograniczenie to nie zlosliwosc wobec administratora, tylko
     * ochrona przed pomylka: 8760 godzin to rok. Blokada "na 100 lat"
     * wpisana przez przypadek byla by skasowaniem konta bez powiedzenia
     * tego wprost - a zakaz naprawde bezterminowy ma teraz wlasne,
     * jawne pole ponizej.
     */
    @Min(value = 1, message = "{validation.ban.hours.min}")
    @Max(value = 8760, message = "{validation.ban.hours.max}")
    @Schema(description = "Ile godzin ma trwac zakaz (1-8760). Pusta wartosc zdejmuje zakaz.",
            example = "24")
    Integer hours,

    /*
     * Osobne pole zamiast umownej liczby godzin (np. -1 albo 999999).
     * Zapytanie ma sie czytac tak, jak brzmi decyzja: "na zawsze" jest
     * innym rodzajem kary niz "na iles godzin", a nie jej skrajna wartoscia.
     * Przy okazji stare zapytania, ktore znaja tylko `hours`, dzialaja
     * dalej bez zmiany - brak tego pola znaczy po prostu "nie na zawsze".
     */
    @Schema(description = "Zakaz bezterminowy. Gdy true, pole hours jest pomijane.",
            example = "false")
    Boolean forever
) {
}
