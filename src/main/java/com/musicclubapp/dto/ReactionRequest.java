package com.musicclubapp.dto;

import com.musicclubapp.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

/**
 * Wybrana reakcja.
 *
 * <p>Pole jest typu {@link ReactionType}, wiec Jackson sam odrzuci napis
 * spoza listy - do serwisu nie ma prawa trafic "SUPER" ani literowka.
 * Nierozpoznana wartosc konczy sie bledem 400 jeszcze przed walidacja.</p>
 */
public record ReactionRequest(

    @NotNull(message = "{validation.reaction.type.required}")
    ReactionType type
) {
}
