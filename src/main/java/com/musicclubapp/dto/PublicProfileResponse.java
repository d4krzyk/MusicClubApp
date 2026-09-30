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
    PresenceResponse presence,
    /** Ogladajacy zablokowal te osobe - profil pokazuje tylko "Odblokuj". */
    boolean blockedByMe,
    /** Profil tylko dla znajomych, a ogladajacy nim nie jest - bez szczegolow. */
    boolean restricted,
    /** Czy ogladajacy moze teraz wyslac zaproszenie (ustawienia tej osoby, blokady). */
    boolean canInvite,
    /** Klan tej osoby - plakietka pod loginem; widoczna takze przy profilu tylko dla znajomych. */
    ClanBadge clan
) {
}
