package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zaproszenie, przekazanie klanu: wskazujemy osobe loginem. */
public record ClanUsernameRequest(@NotBlank @Size(max = 64) String username) {
}
