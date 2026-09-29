package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushUnsubscribeRequest(@NotBlank @Size(max = 1000) String endpoint) {
}
