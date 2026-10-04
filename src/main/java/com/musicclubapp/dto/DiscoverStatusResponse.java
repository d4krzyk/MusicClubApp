package com.musicclubapp.dto;

/**
 * Stan trybu Poznawaj na moim koncie i to, czego brakuje na mojej karcie (do podpowiedzi "dodaj zdjecie").
 */
public record DiscoverStatusResponse(
    boolean enabled,
    int radiusKm,
    /** Moje miasto - bez niego zasieg nic nie robi. */
    String city,
    int photoCount,
    boolean hasBio,
    int promptCount,
    int lookingForCount,
    long favoriteArtistCount,
    int swipesLeft,
    /** Moja karta tak, jak widza ja inni (bez czesci "co nas laczy"). */
    DiscoverCard preview
) {
}
