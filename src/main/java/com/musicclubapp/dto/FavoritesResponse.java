package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Ulubieni artysci i utwory jednej osoby - blok na profilu. */
@Schema(description = "Ulubieni artysci i utwory uzytkownika")
public record FavoritesResponse(
    List<CatalogArtist> artists,
    List<CatalogTrack> tracks,
    boolean canEdit,
    int maxArtists,
    int maxTracks
) {
}
