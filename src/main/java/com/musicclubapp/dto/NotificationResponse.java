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
    LocalDateTime createdAt,

    /** Przypomnienie: ktore wydarzenie i ile dni do niego zostalo (0 = dzis). */
    Long eventId,
    String eventName,
    Integer daysLeft,

    /** Zaproszenie do klanu albo wyrzucenie: ktory klan. */
    Long clanId,
    String clanName,

    /** Spotkanie z czatu (przypomnienie, odwolanie): ktore, gdzie i kiedy sie zaczyna. */
    Long meetingId,
    String meetingPlace,
    java.time.Instant meetingStartsAt,

    /** Ekipa na koncert (prosba, dolaczenie, spotkanie ekipy) - wydarzenie jest w eventId/eventName. */
    Long crewId
) {
}
