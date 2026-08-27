package com.musicclubapp.dto;

import com.musicclubapp.music.MusicProvider;

/**
 * Jedna playlista w gablotce na profilu.
 *
 * <p>Tak jak przy postach: <b>adres odtwarzacza sklada serwer</b>. Frontend
 * dostaje gotowe {@code embedUrl} i nie musi wiedziec, ze YouTube osadza
 * playlisty przez {@code videoseries?list=}, a Apple przez podmiane nazwy
 * serwera.</p>
 */
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
