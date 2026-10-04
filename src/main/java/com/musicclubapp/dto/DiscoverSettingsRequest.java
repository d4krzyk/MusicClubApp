package com.musicclubapp.dto;

/** Wlaczenie trybu Poznawaj i zasieg talii (km; 0 = caly kraj). */
public record DiscoverSettingsRequest(boolean enabled, int radiusKm) {
}
