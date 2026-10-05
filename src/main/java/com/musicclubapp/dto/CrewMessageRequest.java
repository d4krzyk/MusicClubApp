package com.musicclubapp.dto;

import com.musicclubapp.entity.CrewMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nowa wiadomosc na czacie ekipy. */
public record CrewMessageRequest(
    @NotBlank(message = "{validation.crew.chat}")
    @Size(max = CrewMessage.MAX_CONTENT, message = "{validation.crew.chat}")
    String content
) {
}
