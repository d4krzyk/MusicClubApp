package com.musicclubapp.dto;

import com.musicclubapp.entity.CardVisibility;
import jakarta.validation.constraints.NotNull;

/** Kto widzi moja karte na profilu (talia Poznawaj - bez zmian). */
public record CardVisibilityRequest(@NotNull CardVisibility visibility) {
}
