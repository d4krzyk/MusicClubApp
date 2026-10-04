package com.musicclubapp.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Nowa kolejnosc zdjec w galerii: wszystkie numery zdjec, pierwszy = okladka karty. */
public record PhotoOrderRequest(@NotNull List<Long> ids) {
}
