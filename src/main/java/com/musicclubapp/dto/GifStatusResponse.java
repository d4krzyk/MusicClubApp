package com.musicclubapp.dto;

/** Czy GIF-y dzialaja i czyim logiem trzeba je podpisac ("Powered by ..."). */
public record GifStatusResponse(boolean enabled, String attribution) {
}
