package com.musicclubapp.dto;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;

/**
 * Jedna pozycja zestawienia "najczesciej wrzucane" na profilu.
 *
 * @param title      moze byc {@code null}, gdy przy dodawaniu posta serwis
 *                   nie odpowiedzial - frontend pokazuje wtedy sam odnosnik
 * @param postCount  ile razy ta osoba wrzucila to nagranie
 */
public record TopMusicResponse(
    MusicProvider provider,
    MusicKind kind,
    String title,
    String thumbnailUrl,
    /** Adres strony w serwisie - do klikniecia. */
    String url,
    long postCount
) {
}
