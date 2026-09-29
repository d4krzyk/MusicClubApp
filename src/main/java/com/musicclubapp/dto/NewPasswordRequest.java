package com.musicclubapp.dto;

import com.musicclubapp.validation.PasswordsMatch;
import com.musicclubapp.validation.PasswordsToCompare;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nowe haslo z linku resetu - te same zasady co przy rejestracji. */
@PasswordsMatch
public record NewPasswordRequest(
    @NotBlank(message = "{validation.email.token.required}")
    @Size(max = 100, message = "{validation.email.token.required}")
    String token,

    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    String password,

    @NotBlank(message = "{validation.password.confirm.notblank}")
    String confirmPassword
) implements PasswordsToCompare {
}
