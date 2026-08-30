package com.musicclubapp.dto;

import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Jedno powiadomienie w postaci, w jakiej trafia do przegladarki. */
@Schema(description = "Powiadomienie o zdarzeniu dotyczacym uzytkownika")
public record NotificationResponse(
    Long id,
    NotificationType type,
    String actorUsername,
    String actorAvatarUrl,
    ReactionType reactionType,
    Long postId,
    String postExcerpt,

    /** Adres w aplikacji, pod ktory ma przeniesc klikniecie. */
    String link,

    boolean read,
    LocalDateTime createdAt
) {
}
