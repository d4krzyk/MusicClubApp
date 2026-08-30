package com.musicclubapp.dto;

import com.musicclubapp.entity.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Jedna osoba, ktora zareagowala na post - do okienka "kto zareagowal". */
@Schema(description = "Osoba, ktora zareagowala na post")
public record ReactionAuthorResponse(
    String username,
    String avatarUrl,
    ReactionType type,
    LocalDateTime createdAt
) {
}
