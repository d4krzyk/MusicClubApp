package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Artysta znaleziony w katalogu Deezera - wynik wyszukiwania.
 *
 * <p>To DTO, a nie encja: takiego artysty jeszcze <b>nie ma</b> w naszej bazie
 * i moze nigdy nie byc. Wiersz w tabeli {@code artists} powstaje dopiero
 * wtedy, gdy ktos faktycznie doda go do ulubionych.</p>
 */
@Schema(description = "Artysta z katalogu Deezera")
public record CatalogArtist(

    @Schema(description = "Identyfikator w Deezerze", example = "27")
    String externalId,

    @Schema(example = "Daft Punk")
    String name,

    @Schema(description = "Adres zdjecia; moze byc pusty")
    String imageUrl
) {
}
