package com.musicclubapp.dto;

import com.musicclubapp.entity.ParticipationStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Nadchodzace wydarzenie, na ktore zapisal sie ktos z klanu. */
public record ClanEventResponse(
    Long id,
    String name,
    LocalDate startDate,
    LocalTime startTime,
    String city,
    String venueName,
    String imageUrl,
    /** Kto z klanu idzie i chce byc na liscie (ogladajacy zawsze widzi siebie). */
    List<PersonCard> going,
    /** Ilu z klanu idzie - lacznie z tymi, ktorzy nie chca byc na liscie. */
    int goingCount,
    int interestedCount,
    /** Moj zapis albo null. */
    ParticipationStatus mine,
    /** Numer posta klanu pod tym wydarzeniem ("kto jedzie?") albo null, gdy klan jeszcze nie pytal. */
    Long askedPostId
) {
}
