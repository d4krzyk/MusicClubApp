package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanJoinPolicy;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.InvitationStatus;

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
    boolean chatMuted,
    /** Haslo, miasto i gatunki, ktore klan sam o sobie podaje - widoczne dla kazdego zalogowanego. */
    String motto,
    String city,
    List<String> genres,
    ClanJoinPolicy joinPolicy,
    boolean listed,
    /** Moja prosba o dolaczenie (PENDING / DECLINED) albo null. */
    InvitationStatus myRequestStatus,
    /** Ogladajacy moze teraz poprosic o dolaczenie. */
    boolean canRequest,
    /** Numer mojego oczekujacego zaproszenia do tego klanu albo null. */
    Long invitationId,
    /** Prosby o dolaczenie - tylko dla zarzadu klanu. */
    List<ClanJoinRequestResponse> requests,
    /** Tytuly zdefiniowane w klanie - tylko dla czlonkow i administratora aplikacji. */
    List<ClanTitleResponse> titles,
    /** Poziom aktywnosci czatu - dla czlonkow, administratora i - gdy klan jest w przegladarce - dla kazdego. */
    ClanActivityLevel activityLevel
) {
}
