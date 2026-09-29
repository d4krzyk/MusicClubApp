package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Jeden powod dopasowania. Rodzaj idzie osobno od wartosci, bo tekst wokol
 * ("Sluchasz: ...", "You listen to: ...") sklada frontend w jezyku uzytkownika.
 */
@Schema(description = "Dlaczego wydarzenie pasuje do profilu")
public record EventReasonResponse(

    EventReasonKind kind,

    @Schema(description = "Artysta, tytul utworu, gatunki albo liczba znajomych", example = "Dawid Podsiadło")
    String value
) {
}
