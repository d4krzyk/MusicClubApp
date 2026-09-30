package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanRole;

import java.time.LocalDateTime;
import java.util.List;

/** Strona klanu: dane, czlonkowie, glosowanie na kolor i - dla czlonkow - zaproszenia. */
public record ClanResponse(
    Long id,
    String name,
    String tag,
    String description,
    String color,
    String colorHex,
    String iconUrl,
    String photoUrl,
    LocalDateTime createdAt,
    int memberCount,
    int maxMembers,
    /** Rola ogladajacego albo null, gdy nie jest w tym klanie. */
    ClanRole myRole,
    /** Ogladajacy to administrator aplikacji, ktory nie jest czlonkiem - przeglada klan jako moderator. */
    boolean viewingAsAdmin,
    /** Czy ogladajacy widzi czat i posty (czlonek albo administrator aplikacji). */
    boolean canSeeContent,
    List<ClanMemberResponse> members,
    List<ClanColorOption> palette,
    ClanColor myVote,
    /** Oczekujace zaproszenia - tylko dla czlonkow, dla innych pusta lista. */
    List<ClanPendingInvite> invitations,
    /** Przypiete ogloszenie zarzadu - tylko dla czlonkow i administratora aplikacji. */
    String announcement,
    LocalDateTime announcementAt,
    /** Zasady klanu - tylko dla czlonkow i administratora aplikacji (zaproszeni widza je w zaproszeniu). */
    String rules,
    /** Ile wiadomosci na czacie czeka nieprzeczytanych (0 dla niebedacych czlonkami). */
    long unreadChat,
    /** Numer ostatniej przeczytanej wiadomosci - do kreski "nowe wiadomosci" na czacie. */
    long chatReadId,
    boolean chatMuted
) {
}
