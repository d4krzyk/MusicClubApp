package com.musicclubapp.dto;

/**
 * Wynik decyzji. {@code matched} - druga osoba juz wczesniej powiedziala "tak", wiec od tej chwili jestescie
 * znajomymi (wtedy sa tez login i awatar do ekranu "To jest to!").
 */
public record SwipeResponse(boolean matched, String username, String avatarUrl, int swipesLeft) {
}
