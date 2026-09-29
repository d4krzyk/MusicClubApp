package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Jedna osoba z listy uczestnikow. */
@Schema(description = "Uczestnik wydarzenia")
public record AttendeeResponse(

    String username,

    @Schema(description = "Adres zdjecia profilowego; moze byc pusty")
    String avatarUrl,

    @Schema(description = "Czy to moj znajomy")
    boolean friend,

    @Schema(description = "Czy to ja")
    boolean me,

    @Schema(description = "Ukryty przed innymi - widzi to tylko on sam")
    boolean hidden
) {
}
