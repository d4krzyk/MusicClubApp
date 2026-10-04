package com.musicclubapp.dto;

import java.util.List;

/**
 * Kolejne karty talii. {@code radiusActive} - czy zasieg naprawde ogranicza (bez miasta w profilu talia jest
 * z calego kraju); {@code swipesLeft} - ile decyzji zostalo na dzis.
 */
public record DiscoverDeckResponse(List<DiscoverCard> cards, int radiusKm, boolean radiusActive, int swipesLeft) {
}
