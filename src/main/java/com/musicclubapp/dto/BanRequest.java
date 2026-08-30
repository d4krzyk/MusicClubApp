package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Kara nakladana na konto przez administratora. */
@Schema(description = "Kara: liczba godzin, kara bezterminowa "
    + "albo brak obu wartosci, zeby ja zdjac")
public record BanRequest(

    /*
     * Gorne ograniczenie to nie zlosliwosc wobec administratora, tylko ochrona przed pomylka: 8760
     * godzin to rok.
     */
    @Min(value = 1, message = "{validation.ban.hours.min}")
    @Max(value = 8760, message = "{validation.ban.hours.max}")
    @Schema(description = "Ile godzin ma trwac kara (1-8760). Pusta wartosc ja zdejmuje.",
            example = "24")
    Integer hours,

    @Schema(description = "Kara bezterminowa. Gdy true, pole hours jest pomijane.",
            example = "false")
    Boolean forever
) {
}
