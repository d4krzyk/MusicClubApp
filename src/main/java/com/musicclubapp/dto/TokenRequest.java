package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sam token z linku w wiadomosci - przy zgodzie na zmiane adresu i sprawdzeniu linku resetu. */
public record TokenRequest(
    @NotBlank(message = "{validation.email.token.required}")
    @Size(max = 100, message = "{validation.email.token.required}")
    String token
) {
}
