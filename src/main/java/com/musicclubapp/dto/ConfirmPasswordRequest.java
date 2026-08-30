package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Potwierdzenie haslem przy operacji, ktorej nie da sie cofnac.
 *
 * <p>Pole nazywa sie tak samo jak przy zmianie hasla, bo blad "zle haslo"
 * wraca tym samym kanalem - i formularz pokazuje go pod wlasciwym polem.</p>
 */
@Schema(description = "Potwierdzenie wlasnym haslem")
public record ConfirmPasswordRequest(
    @NotBlank
    @Schema(description = "Aktualne haslo zalogowanego", example = "TajneHaslo1")
    String currentPassword
) {
}
