package com.musicclubapp.service;

import com.musicclubapp.entity.EventSource;
import com.musicclubapp.entity.EventStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Wydarzenie ze zrodla pobocznego (Bandsintown, Songkick) w jednej postaci - tym {@link EventMerger} laczy je
 * z naszymi albo zaklada nowe. Daty i godziny lokalne dla miejsca koncertu, jak w Ticketmasterze.
 */
public record ExternalEvent(
    EventSource source,
    String externalId,
    String url,
    String name,
    EventStatus status,
    LocalDate date,
    LocalTime time,
    String venueName,
    String city,
    String countryCode,
    String address,
    Double latitude,
    Double longitude,
    String ticketUrl,
    String imageUrl,
    String description,
    List<String> performers
) {
}
