package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Artysta znaleziony w katalogu Deezera - wynik wyszukiwania. */
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
