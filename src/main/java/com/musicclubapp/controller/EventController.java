package com.musicclubapp.controller;

import com.musicclubapp.dto.AttendeeResponse;
import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventCountryRequest;
import com.musicclubapp.dto.EventDetailsResponse;
import com.musicclubapp.dto.EventView;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.dto.ParticipationRequest;
import com.musicclubapp.dto.ParticipationResponse;
import com.musicclubapp.service.EventParticipationService;
import com.musicclubapp.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final EventParticipationService participationService;

    public EventController(EventService eventService, EventParticipationService participationService) {
        this.eventService = eventService;
        this.participationService = participationService;
    }

    @GetMapping
    @Operation(summary = "Lista wydarzen w jednym z trzech widokow",
        description = "UPCOMING - od najblizszego; FOR_YOU - tylko pasujace do profilu, od najlepiej"
            + " pasujacego, z powodami; MINE - te, na ktore sie zapisalem. W dwoch pierwszych"
            + " kolejne terminy tego samego wydarzenia w tym samym miejscu sa zwiniete w jedna pozycje.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona wydarzen"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Page<EventCardResponse>> list(
            @Parameter(description = "Widok: UPCOMING, FOR_YOU albo MINE")
            @RequestParam(defaultValue = "UPCOMING") EventView view,

            @Parameter(description = "Klucz miasta z /api/events/info, np. krakow")
            @RequestParam(defaultValue = "") String city,

            @Parameter(description = "Szukany tekst: nazwa, miejsce albo wykonawca")
            @RequestParam(defaultValue = "") String q,

            @Parameter(description = "Tylko w promieniu tylu km od miasta z mojego profilu; 0 - caly kraj")
            @RequestParam(defaultValue = "0") int radius,

            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile na stronie (max 50)")
            @RequestParam(defaultValue = "20") int size,

            Authentication authentication) {

        return ResponseEntity.ok(eventService.list(view, city, q, radius,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE)),
            authentication.getName()));
    }

    @GetMapping("/info")
    @Operation(summary = "Miasta do filtra, stan pobierania i to, czy profil ma czym sie dopasowac")
    public ResponseEntity<EventsInfoResponse> info(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(eventService.info(admin, authentication.getName()));
    }

    @PutMapping("/country")
    @Operation(summary = "Zmienia kraj, z ktorego pokazujemy wydarzenia",
        description = "Kraj, ktorego jeszcze nie mamy, zaczyna sie pobierac od razu w tle.")
    public ResponseEntity<EventsInfoResponse> changeCountry(@Valid @RequestBody EventCountryRequest request,
                                                            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(eventService.changeCountry(authentication.getName(), request.country(), admin));
    }

    @PutMapping("/{id}/participation")
    @Operation(summary = "Zainteresowany albo ide - zapis albo jego zmiana")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Moj zapis i liczniki po zmianie"),
        @ApiResponse(responseCode = "400", description = "Wydarzenie minelo albo zostalo wycofane"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego wydarzenia")
    })
    public ResponseEntity<ParticipationResponse> participate(
            @PathVariable Long id,
            @Valid @RequestBody ParticipationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(participationService.participate(
            id, authentication.getName(), request.status(), request.hidden()));
    }

    @DeleteMapping("/{id}/participation")
    @Operation(summary = "Rezygnacja - zapis znika")
    public ResponseEntity<ParticipationResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(participationService.cancel(id, authentication.getName()));
    }

    @GetMapping("/{id}/attendees")
    @Operation(summary = "Kto idzie - bez osob, ktore wybraly \"nie pokazuj mnie\"")
    public ResponseEntity<Page<AttendeeResponse>> attendees(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        return ResponseEntity.ok(participationService.attendees(id, authentication.getName(),
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegoly jednego wydarzenia z pozostalymi terminami")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wydarzenie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego wydarzenia")
    })
    public ResponseEntity<EventDetailsResponse> details(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(eventService.details(id, authentication.getName()));
    }
}
