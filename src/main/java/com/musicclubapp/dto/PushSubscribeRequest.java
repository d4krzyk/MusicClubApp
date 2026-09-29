package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Subskrypcja z przegladarki: PushSubscription.toJSON() rozlozone na pola, plus jezyk tresci. */
public record PushSubscribeRequest(
    @NotBlank @Size(max = 1000) String endpoint,
    @NotBlank @Size(max = 100) String p256dh,
    @NotBlank @Size(max = 40) String auth,
    @Size(max = 8) String lang
) {
}
