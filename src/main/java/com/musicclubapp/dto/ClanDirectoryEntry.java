package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanJoinPolicy;
import com.musicclubapp.entity.InvitationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Klan na liscie w przegladarce: tyle, ile potrzeba do rozeznania sie, "co to za klan" - bez
 * postow, czatu i bez wskazywania osob. Gusta (gatunki) to dane zbiorcze.
 */
public record ClanDirectoryEntry(
    Long id,
    String name,
    String tag,
    String color,
    String colorHex,
    String iconUrl,
    /** Poczatek opisu. */
    String description,
    String motto,
    String city,
    LocalDateTime createdAt,
    int memberCount,
    int maxMembers,
    /** Gatunki, ktore klan sam o sobie podaje. */
    List<String> genres,
    /** Gatunki wspolne dla co najmniej dwoch czlonkow - do trzech najczestszych. */
    List<String> topGenres,
    ClanActivityLevel activityLevel,
    ClanJoinPolicy joinPolicy,
    boolean full,
    /** Dopasowanie do gustu ogladajacego (0 = brak wspolnych wykonawcow i gatunkow). */
    int match,
    /** Gatunki wspolne z gustem ogladajacego - widzi je tylko on. */
    List<String> sharedGenres,
    /** Moja prosba o dolaczenie do tego klanu (PENDING / DECLINED) albo null. */
    InvitationStatus myRequestStatus,
    /** Mam oczekujace zaproszenie do tego klanu. */
    boolean invited,
    /** Ile km od mojego miasta (0 = to samo miasto); null - nie wiadomo albo nie mam miasta. */
    Integer distanceKm
) {
}
