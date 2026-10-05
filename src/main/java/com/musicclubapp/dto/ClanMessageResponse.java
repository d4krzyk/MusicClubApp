package com.musicclubapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Wiadomosc na czacie klanu. */
public record ClanMessageResponse(
    Long id,
    String senderUsername,
    String senderAvatarUrl,
    /** Tresc; pusta, gdy wiadomosc to sam GIF. */
    String content,
    /** GIF dolaczony do wiadomosci albo {@code null}. */
    GifView gif,
    LocalDateTime createdAt,
    boolean mine,
    boolean canDelete,
    /** Numer wiadomosci, na ktora to jest odpowiedz, albo null. */
    Long replyToId,
    /** Skrot tamtej wiadomosci; null, gdy nie ma odpowiedzi albo tamtej nie widac (np. blokada). */
    ClanReplyPreview replyTo,
    List<ClanReactionCount> reactions,
    /** Nagranie rozpoznane w tresci - te same pola co w rozmowach ({@link MessageResponse}); bez nagrania null. */
    String musicEmbedUrl,
    com.musicclubapp.music.MusicProvider musicProvider,
    com.musicclubapp.music.MusicKind musicKind,
    String musicTitle,
    String musicThumbnailUrl,
    Integer musicStartSeconds,
    String musicUrl,
    /** Wiadomosc usunieta - zostaje slad bez tresci. */
    boolean deleted,
    /** Spotkanie, ktore niesie wiadomosc - albo {@code null}. */
    MeetingResponse meeting
) {
}
