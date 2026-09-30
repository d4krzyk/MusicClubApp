package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanEmoji;
import jakarta.validation.constraints.NotNull;

public record ClanReactionRequest(@NotNull ClanEmoji emoji) {
}
