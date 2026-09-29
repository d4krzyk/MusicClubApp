package com.musicclubapp.controller;

import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventDetailsResponse;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Zakladka Wydarzenia: koncerty pobrane z Ticketmastera. */
@RestController
@RequestMapping("/api/events")
@Tag(name = "Wydarzenia", description = "Koncerty w Polsce - lista, szczegoly, miasta")
public class EventController {

    /** Zabezpieczenie przed {@code ?size=1000000}. */
    private static final int MAX_SIZE = 50;

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @Operation(summary = "Nadchodzace wydarzenia od najblizszego",
        description = "Kolejne terminy tego samego wydarzenia w tym samym miejscu sa zwiniete"
            + " w jedna pozycje z liczba pozostalych terminow.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona wydarzen"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Page<EventCardResponse>> list(
            @Parameter(description = "Klucz miasta z /api/events/info, np. krakow")
            @RequestParam(defaultValue = "") String city,

            @Parameter(description = "Szukany tekst: nazwa, miejsce albo wykonawca")
            @RequestParam(defaultValue = "") String q,

            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile na stronie (max 50)")
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(eventService.list(city, q,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    @GetMapping("/info")
    @Operation(summary = "Miasta do filtra i stan pobierania wydarzen")
    public ResponseEntity<EventsInfoResponse> info(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(eventService.info(admin));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegoly jednego wydarzenia z pozostalymi terminami")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wydarzenie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego wydarzenia")
    })
    public ResponseEntity<EventDetailsResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.details(id));
    }
}
