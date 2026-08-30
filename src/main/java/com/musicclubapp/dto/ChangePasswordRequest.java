package com.musicclubapp.dto;

import com.musicclubapp.validation.PasswordsToCompare;
import com.musicclubapp.validation.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zmiana wlasnego hasla. */
@PasswordsMatch
public record ChangePasswordRequest(

    @NotBlank(message = "{validation.password.current.notblank}")
    String currentPassword,

    /** Nowe haslo. */
    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    String password,

    /** Powtorzenie nowego hasla. */
    @NotBlank(message = "{validation.password.confirm.notblank}")
    String confirmPassword

) implements PasswordsToCompare {
}
