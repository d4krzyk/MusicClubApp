package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Czy ktos jest teraz aktywny i kiedy byl ostatnio. */
public record PresenceResponse(
    boolean online,
    LocalDateTime lastSeenAt,
    /**
     * Aktywnosc niewidoczna dla ogladajacego: ta osoba ja ukrywa albo jest
     * blokada. Frontend nie pokazuje wtedy kropki - "nigdy nie byl aktywny"
     * byloby nieprawda.
     */
    boolean hidden
) {
}
