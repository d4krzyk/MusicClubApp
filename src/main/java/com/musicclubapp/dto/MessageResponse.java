package com.musicclubapp.dto;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;

import java.time.LocalDateTime;

/** Jedna wiadomosc wysylana do przegladarki. */
public record MessageResponse(
    Long id,
    String senderUsername,
    String senderAvatarUrl,
    /** Tresc; {@code null} albo pusta, gdy wyslano sam utwor. */
    String content,
    /** GIF dolaczony do wiadomosci albo {@code null}. */
    GifView gif,
    /** Gotowy adres odtwarzacza do {@code <iframe>} albo {@code null}. */
    String musicEmbedUrl,
    MusicProvider musicProvider,
    MusicKind musicKind,
    String musicTitle,
    String musicThumbnailUrl,
    Integer musicStartSeconds,
    /** Adres strony w serwisie - pod przycisk "otworz w serwisie". */
    String musicUrl,
    LocalDateTime createdAt,
    boolean mine,
    boolean read
) {
}
