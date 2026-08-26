package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Utwor znaleziony w katalogu Deezera - wynik wyszukiwania. */
@Schema(description = "Utwor z katalogu Deezera")
public record CatalogTrack(

    @Schema(description = "Identyfikator utworu w Deezerze", example = "3135556")
    String externalId,

    @Schema(example = "Harder, Better, Faster, Stronger")
    String title,

    @Schema(example = "Daft Punk")
    String artistName,

    @Schema(description = "Identyfikator wykonawcy w Deezerze - sluzy do dopasowywania gustow")
    String artistExternalId,

    @Schema(description = "Adres okladki albumu; moze byc pusty")
    String imageUrl
) {
}
