package com.musicclubapp.dto;

import com.musicclubapp.entity.Clan;
import jakarta.validation.constraints.Size;

/** Zmiana klanu. Nazwe i skrot zmienia tylko zalozyciel; puste pola = bez zmiany. */
public record UpdateClanRequest(
    @Size(max = 64) String name,
    @Size(max = 16) String tag,
    @Size(max = Clan.DESCRIPTION_MAX) String description
) {
}
