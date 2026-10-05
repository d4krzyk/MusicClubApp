package com.musicclubapp.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Strona ekipy: karta, wydarzenie, sklad, prosby (tylko dla zakladajacego), nieprzeczytane na czacie. */
public record CrewDetailsResponse(
    CrewCardResponse crew,
    Long eventId,
    String eventName,
    LocalDate eventDate,
    LocalTime eventTime,
    String venueName,
    String eventCity,
    String eventImageUrl,
    boolean eventPast,
    List<CrewPersonView> members,
    List<CrewRequestView> requests,
    long unreadChat,
    boolean chatOpen
) {
}
