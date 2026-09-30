package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanColor;
import jakarta.validation.constraints.NotNull;

public record ClanColorRequest(@NotNull ClanColor color) {
}
