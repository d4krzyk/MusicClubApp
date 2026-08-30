package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Karta osoby na liscie proponowanych znajomych. */
@Schema(description = "Proponowany znajomy wraz z powodem dopasowania")
public record SuggestionResponse(
    String username,
    String avatarUrl,
    long mutualFriends,
    long sharedArtists,
    long sharedGenres,
    boolean alreadyFriend,
    boolean matched
) {
}
