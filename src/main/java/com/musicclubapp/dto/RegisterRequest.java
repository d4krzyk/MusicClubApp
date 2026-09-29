package com.musicclubapp.dto;

import com.musicclubapp.validation.PasswordsToCompare;
import com.musicclubapp.validation.PasswordsMatch;
import com.musicclubapp.validation.UniqueUsername;
import com.musicclubapp.validation.NotDisposableEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dane przysylane przez formularz rejestracji. */
@PasswordsMatch
public record RegisterRequest(

    @NotBlank(message = "{validation.username.notblank}")
    @Size(min = 3, max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "{validation.username.pattern}")
    @UniqueUsername
    String username,

    @NotBlank(message = "{validation.email.notblank}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    @NotDisposableEmail
    String email,

    /* Minimum 8 znakow to rozsadne minimum. */
    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    String password,

    /** Powtorzenie hasla - wyklad 7, slajd 35. Sprawdzane przez {@link PasswordsMatch}. */
    @NotBlank(message = "{validation.password.confirm.notblank}")
    String confirmPassword

) implements PasswordsToCompare {
}
