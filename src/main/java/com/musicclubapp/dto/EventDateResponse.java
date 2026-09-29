package com.musicclubapp.dto;

import com.musicclubapp.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/** Jeden termin z serii - lista "inne terminy" na stronie wydarzenia. */
@Schema(description = "Termin wydarzenia")
public record EventDateResponse(Long id, LocalDate date, LocalTime time, EventStatus status) {
}
