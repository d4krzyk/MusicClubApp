package com.musicclubapp.dto;

import com.musicclubapp.entity.CrewRequest;
import jakarta.validation.constraints.Size;

/** Dolaczenie albo prosba: przy naborze "za zgoda" krotka wiadomosc do zakladajacego (opcjonalna). */
public record CrewJoinRequest(
    @Size(max = CrewRequest.MAX_MESSAGE, message = "{validation.crew.message}")
    String message
) {
}
