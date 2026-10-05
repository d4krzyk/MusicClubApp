package com.musicclubapp.dto;

import com.musicclubapp.entity.Crew;
import com.musicclubapp.entity.CrewJoinPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Zakladanie i zmiana ekipy. Puste tytul, opis i miasto = bez nich. */
public record CrewForm(
    @Size(max = Crew.MAX_TITLE, message = "{validation.crew.title}")
    String title,
    @Size(max = Crew.MAX_DESCRIPTION, message = "{validation.crew.description}")
    String description,
    @NotNull(message = "{validation.crew.capacity}")
    @Min(value = Crew.MIN_CAPACITY, message = "{validation.crew.capacity}")
    @Max(value = Crew.MAX_CAPACITY, message = "{validation.crew.capacity}")
    Integer capacity,
    @NotNull(message = "{validation.crew.policy}")
    CrewJoinPolicy joinPolicy,
    @Size(max = Crew.MAX_CITY, message = "{validation.crew.city}")
    String departureCity
) {
}
