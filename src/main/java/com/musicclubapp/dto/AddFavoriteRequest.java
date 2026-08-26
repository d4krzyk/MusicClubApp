package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Dodanie pozycji do ulubionych.
 *
 * <p><b>Jedno pole i to nie przypadek.</b> Nazwa, zdjecie i wykonawca NIE sa
 * tu przyjmowane - serwer pobiera je sam z Deezera na podstawie
 * identyfikatora. Gdyby przychodzily z zapytania, kazdy moglby wyslac
 * {@code {"externalId":"1","name":"Zespol Ktorego Nie Ma"}} z pominieciem
 * przegladarki i cala zasada "tylko z katalogu" bylaby ozdoba.</p>
 */
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
