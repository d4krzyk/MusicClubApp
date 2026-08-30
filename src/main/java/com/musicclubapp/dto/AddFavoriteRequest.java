package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Dodanie pozycji do ulubionych. */
@Schema(description = "Identyfikator pozycji z katalogu Deezera")
public record AddFavoriteRequest(

    @NotBlank(message = "{validation.favorite.id.required}")
    // Identyfikatory Deezera sa liczbami; wzorzec odsiewa smieci, zanim
    // cokolwiek doklejymy do adresu zapytania
    @Pattern(regexp = "\\d{1,20}", message = "{validation.favorite.id.invalid}")
    @Schema(example = "27")
    String externalId
) {
}
