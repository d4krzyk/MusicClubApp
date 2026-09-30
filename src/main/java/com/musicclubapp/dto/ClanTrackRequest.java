package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanTrack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Propozycja utworu tygodnia: link do utworu i krotki dopisek. */
public record ClanTrackRequest(@NotBlank @Size(max = 500) String url,
                               @Size(max = ClanTrack.NOTE_MAX) String note) {
}
