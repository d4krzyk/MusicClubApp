package com.musicclubapp.dto;

import com.musicclubapp.entity.EventStatus;
import com.musicclubapp.entity.ParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Jedna karta na liscie wydarzen - najblizszy termin serii. */
@Schema(description = "Wydarzenie na liscie")
public record EventCardResponse(

    Long id,

    @Schema(example = "Strachy na Lachy")
    String name,

    @Schema(description = "Dzien wydarzenia, czas polski", example = "2026-10-02")
    LocalDate date,

    @Schema(description = "Godzina rozpoczecia; pusta, gdy jeszcze nieznana", example = "20:00:00")
    LocalTime time,

    EventStatus status,

    @Schema(example = "Klub Tama")
    String venueName,

    @Schema(description = "Miasto tak, jak podal je Ticketmaster", example = "Poznan")
    String city,

    @Schema(description = "Miasto w jednej postaci - klucz filtra", example = "poznan")
    String cityKey,

    @Schema(description = "Mniejsze zdjecie na liste; moze byc puste")
    String thumbUrl,

    @Schema(description = "Kto gra - pierwsze nazwiska ze skladu")
    List<String> performers,

    @Schema(example = "Rock")
    String genre,

    @Schema(description = "Ile JESZCZE terminow ma ta seria poza pokazanym", example = "0")
    long moreDates,

    @Schema(description = "Moj zapis na TEN termin; pusty, gdy nic nie zaznaczylem")
    ParticipationStatus myStatus,

    @Schema(description = "Ile osob idzie - razem z ukrytymi", example = "12")
    long going,

    @Schema(description = "Ile osob jest zainteresowanych", example = "30")
    long interested,

    @Schema(description = "Ilu moich znajomych sie zapisalo", example = "2")
    long friends,

    @Schema(description = "Dlaczego pasuje do profilu - tylko w widoku \"Dla ciebie\"")
    List<EventReasonResponse> reasons,

    @Schema(description = "Ticketmaster juz go nie ma - pokazywane tylko zapisanym, w zakladce \"Moje\"")
    boolean withdrawn,

    @Schema(description = "Ile km od miasta z mojego profilu; 0 = to samo miasto, pusty = nie wiadomo albo nie mam miasta")
    Integer distanceKm
) {
}
