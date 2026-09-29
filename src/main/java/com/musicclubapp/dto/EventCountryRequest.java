package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zmiana kraju, z ktorego pokazujemy wydarzenia. */
@Schema(description = "Kraj wydarzen")
public record EventCountryRequest(

    @NotBlank(message = "{validation.event.country.required}")
    @Size(min = 2, max = 2, message = "{validation.event.country.required}")
    @Schema(description = "Kod kraju ISO", example = "DE")
    String country
) {
}
