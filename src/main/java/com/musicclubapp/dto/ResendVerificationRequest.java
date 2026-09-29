package com.musicclubapp.dto;

import com.musicclubapp.validation.NotDisposableEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Wyslij link ponownie" przed pierwszym zalogowaniem.
 *
 * <p>Z loginem i haslem - tymi samymi, ktore wlasnie nie wpuscily na konto.
 * Bez hasla kazdy, kto zna czyjs login, moglby zasypywac jego skrzynke.
 * Nowy adres jest opcjonalny: pozwala poprawic literowke z rejestracji.</p>
 */
public record ResendVerificationRequest(
    @NotBlank(message = "{validation.username.notblank}")
    String username,

    @NotBlank(message = "{validation.password.notblank}")
    String password,

    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    @NotDisposableEmail
    String email
) {
}
