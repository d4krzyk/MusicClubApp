package com.musicclubapp.dto;

import com.musicclubapp.entity.ParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Zapis na wydarzenie albo jego zmiana. */
@Schema(description = "Zainteresowany albo ide")
public record ParticipationRequest(

    @NotNull(message = "{validation.participation.status.required}")
    ParticipationStatus status,

    @Schema(description = "Nie pokazuj mnie na liscie uczestnikow; brak = jak dotad "
        + "(przy nowym zapisie - domyslne z ustawien prywatnosci)")
    Boolean hidden
) {
}
