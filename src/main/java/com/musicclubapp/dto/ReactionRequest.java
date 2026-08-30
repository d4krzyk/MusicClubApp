package com.musicclubapp.dto;

import com.musicclubapp.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

/** Wybrana reakcja. */
public record ReactionRequest(

    @NotNull(message = "{validation.reaction.type.required}")
    ReactionType type
) {
}
