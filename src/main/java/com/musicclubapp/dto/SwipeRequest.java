package com.musicclubapp.dto;

import com.musicclubapp.entity.SwipeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Decyzja o osobie z talii. */
public record SwipeRequest(@NotBlank String username, @NotNull SwipeDecision decision) {
}
