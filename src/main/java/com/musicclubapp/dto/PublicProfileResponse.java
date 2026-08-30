package com.musicclubapp.dto;

import java.time.LocalDateTime;

/** Profil uzytkownika widziany przez INNYCH. */
public record PublicProfileResponse(
    String username,
    String avatarUrl,
    LocalDateTime createdAt,
    long postCount,
    boolean self,
    /** Ilu ma znajomych - liczba nad paskiem znajomych. */
    long friendCount,
    /** W jakiej relacji jest z nim ogladajacy. */
    FriendshipStatus friendshipStatus,
    /** Czy jest teraz aktywny i kiedy byl ostatnio. */
    PresenceResponse presence
) {
}
