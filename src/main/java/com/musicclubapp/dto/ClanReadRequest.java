package com.musicclubapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** "Przeczytalem czat do tej wiadomosci wlacznie". */
public record ClanReadRequest(@NotNull @PositiveOrZero Long upTo) {
}
