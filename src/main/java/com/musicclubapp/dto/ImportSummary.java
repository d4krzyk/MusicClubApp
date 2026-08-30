package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Co dokladnie zrobil import z Last.fm. */
@Schema(description = "Podsumowanie importu z Last.fm")
public record ImportSummary(
    int addedArtists,
    int addedTracks,
    int skipped,
    int alreadyPresent,
    boolean limitReached
) {
}
