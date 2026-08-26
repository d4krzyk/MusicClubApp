package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Import historii sluchania z Last.fm.
 *
 * <p>Podajemy sama <b>nazwe uzytkownika Last.fm</b> - bez hasla i bez
 * logowania. Historia sluchania jest tam domyslnie publiczna, a API oddaje ja
 * kazdemu, kto ma klucz aplikacji. Dlatego nie prosimy o zadne dane logowania
 * i nie ma tu czego wykrasc.</p>
 */
@Schema(description = "Nazwa uzytkownika w serwisie Last.fm")
public record ImportLastFmRequest(

    @NotBlank(message = "{validation.lastfm.username.required}")
    @Size(max = 100, message = "{validation.lastfm.username.size}")
    @Schema(example = "rj")
    String username
) {
}
