package com.musicclubapp.controller;

import com.musicclubapp.dto.ClanJoinRequestRequest;
import com.musicclubapp.dto.ClanResponse;
import com.musicclubapp.service.ClanRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prosby o dolaczenie do klanu. */
@RestController
@RequestMapping("/api/clans/{id}/requests")
@Tag(name = "Klany", description = "Przegladarka klanow, prosby o dolaczenie, tytuly, ankiety i ranking")
public class ClanRequestController {

    private final ClanRequestService requests;

    public ClanRequestController(ClanRequestService requests) {
        this.requests = requests;
    }

    @PostMapping
    @Operation(summary = "Prosi o dolaczenie do klanu (klan musi przyjmowac prosby)")
    public ResponseEntity<ClanResponse> request(@PathVariable Long id,
                                                @Valid @RequestBody(required = false) ClanJoinRequestRequest payload,
                                                Authentication auth) {
        String wstep = payload == null ? null : payload.message();
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.request(id, auth.getName(), wstep));
    }

    @DeleteMapping("/mine")
    @Operation(summary = "Cofa moja oczekujaca prosbe")
    public ResponseEntity<ClanResponse> withdraw(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(requests.withdraw(id, auth.getName()));
    }

    @PostMapping("/{requestId}/accept")
    @Operation(summary = "Przyjmuje prosbe (zarzad klanu)")
    public ResponseEntity<ClanResponse> accept(@PathVariable Long id, @PathVariable Long requestId,
                                               Authentication auth) {
        return ResponseEntity.ok(requests.accept(id, requestId, auth.getName()));
    }

    @PostMapping("/{requestId}/decline")
    @Operation(summary = "Odrzuca prosbe (zarzad klanu) - bez powiadomienia i bez podawania powodu")
    public ResponseEntity<ClanResponse> decline(@PathVariable Long id, @PathVariable Long requestId,
                                                Authentication auth) {
        return ResponseEntity.ok(requests.decline(id, requestId, auth.getName()));
    }
}
