package com.musicclubapp.dto;

import jakarta.validation.constraints.NotNull;

public record ClanPollVoteRequest(@NotNull Long optionId) {
}
