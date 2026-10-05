package com.musicclubapp.dto;

import com.musicclubapp.entity.MeetingStatus;

import java.time.Instant;
import java.util.List;

/** Spotkanie, jak widzi je ogladajacy (jego odpowiedz, czy moze odpowiadac, kto bedzie - bez osob z jego blokad). */
public record MeetingResponse(
    Long id,
    String creator,
    /** Ogladajacy je zalozyl - moze odwolac. */
    boolean mine,
    String place,
    String note,
    Double latitude,
    Double longitude,
    Instant startsAt,
    Instant endsAt,
    int remindMinutes,
    boolean cancelled,
    /** GOING, NOT_GOING albo null (bez odpowiedzi). */
    MeetingStatus myStatus,
    int goingCount,
    int notGoingCount,
    /** Loginy potwierdzonych (zalozyciel pierwszy), najwyzej kilka - reszta jest w liczniku. */
    List<String> going,
    /** Czy ogladajacy moze odpowiadac: strona rozmowy, ktora nadal jest znajomoscia, albo czlonek klanu. */
    boolean canRespond,
    Instant updatedAt
) {
}
