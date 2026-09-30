package com.musicclubapp.dto;

import com.musicclubapp.entity.Clan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dane nowego klanu. Zasady nazwy i skrotu sprawdza serwis - komunikaty zalezne od jezyka. */
public record CreateClanRequest(
    @NotBlank @Size(max = 64) String name,
    @NotBlank @Size(max = 16) String tag,
    @Size(max = Clan.DESCRIPTION_MAX) String description
) {
}
