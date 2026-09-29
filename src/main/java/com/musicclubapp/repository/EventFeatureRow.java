package com.musicclubapp.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/** Wydarzenie w wersji do dopasowania "Dla ciebie" - tylko to, co do niego potrzebne. */
public interface EventFeatureRow {

    Long getId();

    String getSeriesKey();

    String getName();

    LocalDate getStartDate();

    LocalTime getStartTime();

    String getVenueName();

    String getCityKey();

    String getGenre();

    String getSubGenre();
}
