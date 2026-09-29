package com.musicclubapp.dto;

import com.musicclubapp.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Wszystko o jednym wydarzeniu - strona wydarzenia. */
@Schema(description = "Szczegoly wydarzenia")
public record EventDetailsResponse(
    Long id,
    String name,
    LocalDate date,
    LocalTime time,
    EventStatus status,

    @Schema(description = "Opis od organizatora; bywa pusty")
    String description,

    @Schema(description = "Strona wydarzenia u Ticketmastera - tam sa bilety")
    String ticketUrl,

    String imageUrl,
    String venueName,
    String city,
    String cityKey,
    String address,
    Double latitude,
    Double longitude,
    String genre,
    String subGenre,
    @Schema(description = "Kto gra, w kolejnosci z plakatu")
    List<String> performers,

    @Schema(description = "Pozostale nadchodzace terminy tej samej serii")
    List<EventDateResponse> otherDates,

    @Schema(description = "Czy wydarzenie juz sie odbylo")
    boolean past,

    @Schema(description = "Ticketmaster juz go nie ma, a ktos byl zapisany - moglo zostac odwolane")
    boolean withdrawn,

    ParticipationResponse participation,

    @Schema(description = "Ilu moich znajomych sie zapisalo")
    long friends,

    @Schema(description = "Dlaczego pasuje do profilu; pusta lista, gdy nie pasuje")
    List<EventReasonResponse> reasons,

    @Schema(description = "Pierwsze osoby z listy uczestnikow; reszta pod /attendees")
    List<AttendeeResponse> attendees
) {
}
