package com.musicclubapp.dto;

import com.musicclubapp.entity.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Jedna osoba, ktora zareagowala na post - do okienka "kto zareagowal".
 *
 * <p>Sama liczba przy emotce mowi, ILE osob, ale nie mowi KTO. Przy paru
 * reakcjach to jedyna rzecz, ktora naprawde interesuje autora posta.</p>
 */
@Schema(description = "Osoba, ktora zareagowala na post")
public record ReactionAuthorResponse(
    String username,
    String avatarUrl,
    ReactionType type,
    LocalDateTime createdAt
) {
}
