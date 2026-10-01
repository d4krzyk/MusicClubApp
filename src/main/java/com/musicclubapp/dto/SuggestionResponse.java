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
    boolean matched,
    /** SAME_CITY / NEARBY (do ok. 60 km) albo null - bez dokladnej odleglosci; tylko dla osob, ktore pokazuja miasto. */
    String proximity,
    /** Miasto tej osoby - tylko gdy je pokazuje i ma jawny profil. */
    String city
) {
}
