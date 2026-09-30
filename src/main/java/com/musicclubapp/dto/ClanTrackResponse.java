package com.musicclubapp.dto;

import com.musicclubapp.music.MusicProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Propozycja utworu tygodnia w klanie. */
public record ClanTrackResponse(
    Long id,
    String proposerUsername,
    String proposerAvatarUrl,
    String title,
    String thumbnailUrl,
    MusicProvider provider,
    /** Adres odtwarzacza do {@code <iframe>}. */
    String embedUrl,
    /** Adres strony w serwisie - pod "otworz w serwisie". */
    String pageUrl,
    String note,
    long votes,
    boolean iVoted,
    /** Prowadzi w glosowaniu (co najmniej jeden glos). */
    boolean leader,
    boolean canDelete,
    LocalDate weekStart,
    LocalDateTime createdAt
) {
}
