package com.musicclubapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Wiadomosc na czacie klanu. */
public record ClanMessageResponse(
    Long id,
    String senderUsername,
    String senderAvatarUrl,
    String content,
    LocalDateTime createdAt,
    boolean mine,
    boolean canDelete,
    /** Numer wiadomosci, na ktora to jest odpowiedz, albo null. */
    Long replyToId,
    /** Skrot tamtej wiadomosci; null, gdy nie ma odpowiedzi albo tamtej nie widac (np. blokada). */
    ClanReplyPreview replyTo,
    List<ClanReactionCount> reactions
) {
}
