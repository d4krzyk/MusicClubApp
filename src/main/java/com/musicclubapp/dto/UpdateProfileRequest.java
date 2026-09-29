package com.musicclubapp.dto;

import com.musicclubapp.validation.NotDisposableEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Zmiana loginu i adresu e-mail we wlasnym profilu. */
public record UpdateProfileRequest(

    @NotBlank(message = "{validation.username.notblank}")
    @Size(min = 3, max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "{validation.username.pattern}")
    String username,

    @NotBlank(message = "{validation.email.notblank}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    @NotDisposableEmail
    String email,

    /**
     * Obecne haslo - wymagane tylko przy zmianie adresu. Sesja mogla zostac
     * otwarta na cudzym komputerze, a adres to klucz do odzyskania konta.
     */
    String currentPassword
) {
}
