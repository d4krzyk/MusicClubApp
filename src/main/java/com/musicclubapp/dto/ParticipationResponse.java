package com.musicclubapp.dto;

import com.musicclubapp.entity.ParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Moj zapis i liczniki po zmianie - ekran odswieza sie bez drugiego zapytania. */
@Schema(description = "Stan zapisow na wydarzenie")
public record ParticipationResponse(

    @Schema(description = "Moj zapis; pusty po rezygnacji")
    ParticipationStatus myStatus,

    @Schema(description = "Czy jestem ukryty na liscie uczestnikow")
    boolean hidden,

    @Schema(description = "Ile osob idzie - razem z ukrytymi")
    long going,

    long interested,

    @Schema(description = "Ile z idacych nie chce byc na liscie")
    long hiddenGoing
) {
}
