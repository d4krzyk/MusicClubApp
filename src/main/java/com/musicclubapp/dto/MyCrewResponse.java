package com.musicclubapp.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Moja ekipa na nadchodzacy koncert - do "Twoje koncerty". */
public record MyCrewResponse(
    Long crewId,
    String title,
    int members,
    int capacity,
    long unreadChat,
    Long eventId,
    String eventName,
    LocalDate eventDate,
    LocalTime eventTime,
    String venueName,
    String eventCity,
    String eventImageUrl
) {
}
