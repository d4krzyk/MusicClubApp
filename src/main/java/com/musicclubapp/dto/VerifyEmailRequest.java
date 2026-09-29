package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token z linku w wiadomosci. */
public record VerifyEmailRequest(
    @NotBlank(message = "{validation.email.token.required}")
    @Size(max = 100, message = "{validation.email.token.required}")
    String token
) {
}
