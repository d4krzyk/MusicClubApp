package com.musicclubapp.dto;

import com.musicclubapp.entity.Meeting;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Nowe spotkanie w rozmowie albo na czacie klanu. Czas jako chwila (ISO z "Z"), punkt na mapie opcjonalny - ale
 * oba wspolrzedne albo zadna. Zasady czasu (nie w przeszlosc, najwyzej 60 dni naprzod, najwyzej doba) i lista
 * przypomnien sprawdza serwis.
 */
public record MeetingRequest(
    @NotBlank(message = "{validation.meeting.place}")
    @Size(max = Meeting.MAX_PLACE, message = "{validation.meeting.place}")
    String place,

    @Size(max = Meeting.MAX_NOTE, message = "{validation.meeting.note}")
    String note,

    @DecimalMin(value = "-90.0", message = "{validation.meeting.point}")
    @DecimalMax(value = "90.0", message = "{validation.meeting.point}")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "{validation.meeting.point}")
    @DecimalMax(value = "180.0", message = "{validation.meeting.point}")
    Double longitude,

    @NotNull(message = "{validation.meeting.start}")
    Instant startsAt,

    @NotNull(message = "{validation.meeting.end}")
    Instant endsAt,

    /** Minuty przed poczatkiem: 0 (bez), 15, 30, 60, 120 albo 1440. */
    @NotNull(message = "{validation.meeting.remind}")
    Integer remindMinutes
) {
}
