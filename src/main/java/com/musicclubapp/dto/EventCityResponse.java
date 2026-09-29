package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Miasto do filtra na liscie wydarzen. */
@Schema(description = "Miasto z liczba wydarzen")
public record EventCityResponse(

    @Schema(example = "krakow")
    String key,

    @Schema(description = "Nazwa od Ticketmastera; frontend podmienia ja na polska", example = "Krakow")
    String name,

    @Schema(example = "57")
    long events
) {
}
