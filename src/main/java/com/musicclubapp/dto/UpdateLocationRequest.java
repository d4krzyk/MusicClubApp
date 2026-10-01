package com.musicclubapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Miasto z profilu. Pusty tekst (albo brak) = usuniecie miasta. */
public record UpdateLocationRequest(

    @Size(max = 60, message = "{validation.city.size}")
    @Pattern(regexp = "^\\s*$|^\\s*\\p{L}[\\p{L} .'’-]*$", message = "{validation.city.pattern}")
    String city
) {
}
