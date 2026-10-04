package com.musicclubapp.entity;

/**
 * "Szukam" na karcie profilu - po co ktos chce poznawac ludzi. Do {@link #MAX} pozycji na osobe; zapisane w
 * jednej kolumnie {@code users.looking_for} jako nazwy oddzielone przecinkami ({@link LookingForConverter}).
 * Napisy sa w tlumaczeniach przegladarki - tu tylko nazwy.
 */
public enum LookingFor {
    CONCERT_BUDDIES,
    FESTIVALS,
    JAMMING,
    MUSIC_TALK,
    NEW_MUSIC,
    RECORD_SWAPS,
    PARTIES,
    PLAYLISTS;

    /** Najwyzej tyle pozycji na jednej karcie. */
    public static final int MAX = 3;
}
