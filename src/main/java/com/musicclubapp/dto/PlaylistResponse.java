package com.musicclubapp.dto;

import com.musicclubapp.music.MusicProvider;

/** Jedna playlista w gablotce na profilu. */
public record PlaylistResponse(
    Long id,
    MusicProvider provider,
    /** Tytul z serwisu; moze byc {@code null}, gdy nie odpowiedzial przy dodawaniu. */
    String title,
    String thumbnailUrl,
    /** Gotowy adres do {@code <iframe>}. */
    String embedUrl,
    /** Adres strony w serwisie - do otwarcia w nowej karcie. */
    String pageUrl
) {
}
