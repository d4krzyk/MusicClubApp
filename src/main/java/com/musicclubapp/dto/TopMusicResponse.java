package com.musicclubapp.dto;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;

/** Jedna pozycja zestawienia "najczesciej wrzucane" na profilu. */
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
