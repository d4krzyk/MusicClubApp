package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Co jest potrzebne, zanim pokaze sie lista: miasta i stan importu. */
@Schema(description = "Miasta i stan pobierania wydarzen")
public record EventsInfoResponse(

    @Schema(description = "Czy serwer ma klucz Ticketmastera. Bez niego lista zawsze jest pusta")
    boolean configured,

    List<EventCityResponse> cities,

    @Schema(description = "Ostatni import - TYLKO dla administratora, pozostali dostaja null")
    ImportInfo lastImport
) {

    @Schema(description = "Wynik ostatniego importu")
    public record ImportInfo(LocalDateTime finishedAt, boolean success, int events,
                             int removed, String error) {
    }
}
