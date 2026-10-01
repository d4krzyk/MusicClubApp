package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanActivityMetric;
import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanTitleMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nowy tytul albo zmiana istniejacego. Metryka i prog tylko przy trybie AUTO. */
public record ClanTitleRequest(
    @NotBlank @Size(max = 40) String name,
    @NotNull ClanColor color,
    @NotNull ClanTitleMode mode,
    ClanActivityMetric metric,
    @Min(1) @Max(100000) Integer threshold
) {
}
