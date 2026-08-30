package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;

/** Dane z formularza logowania. */
public record LoginRequest(

    @NotBlank(message = "{validation.username.notblank}")
    String username,

    @NotBlank(message = "{validation.password.notblank}")
    String password,

    /** Czy zapamietac zalogowanie (wymaganie nr 17, wyklad 7 slajdy 44-46). */
    boolean rememberMe

) {
}
