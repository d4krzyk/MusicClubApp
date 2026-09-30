package com.musicclubapp.dto;

import jakarta.validation.constraints.NotNull;

public record ClanMuteRequest(@NotNull Boolean muted) {
}
