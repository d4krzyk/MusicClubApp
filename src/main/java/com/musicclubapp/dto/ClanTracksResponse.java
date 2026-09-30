package com.musicclubapp.dto;

import java.time.LocalDate;
import java.util.List;

/** Utwor tygodnia: propozycje z biezacego tygodnia i zwyciezcy poprzednich. */
public record ClanTracksResponse(
    LocalDate weekStart,
    LocalDate weekEnd,
    /** Ile propozycji moze dodac jedna osoba w tygodniu. */
    int perPerson,
    /** Ile jeszcze moze dodac ogladajacy w tym tygodniu. */
    int remaining,
    List<ClanTrackResponse> tracks,
    /** Zwyciezcy poprzednich tygodni (najnowszy pierwszy), tylko tygodnie z glosami. */
    List<ClanTrackResponse> previous
) {
}
