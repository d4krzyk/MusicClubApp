package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Czy ktos jest teraz aktywny i kiedy byl ostatnio. */
public record PresenceResponse(
    boolean online,
    LocalDateTime lastSeenAt
) {
}
