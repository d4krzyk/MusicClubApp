package com.musicclubapp.controller;

import com.musicclubapp.dto.AddFavoriteRequest;
import com.musicclubapp.dto.ImportLastFmRequest;
import com.musicclubapp.dto.ImportSummary;
import com.musicclubapp.dto.FavoritesResponse;
import com.musicclubapp.service.FavoritesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Ulubieni artysci i utwory zalogowanego uzytkownika. */
@RestController
@RequestMapping("/api/profile/favorites")
@Tag(name = "Ulubione", description = "Ulubieni artysci i utwory na profilu")
public class FavoritesController {

    private final FavoritesService favoritesService;

    public FavoritesController(FavoritesService favoritesService) {
        this.favoritesService = favoritesService;
    }

    @GetMapping
    @Operation(summary = "Moi ulubieni artysci i utwory")
    public ResponseEntity<FavoritesResponse> mine(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(favoritesService.favorites(username, username));
    }

    @PostMapping("/artists")
    @Operation(summary = "Dodaje artyste z katalogu Deezera do ulubionych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zaktualizowana lista ulubionych"),
        @ApiResponse(responseCode = "409",
            description = "Osiagnieto limit albo takiego artysty nie ma w katalogu")
    })
    public ResponseEntity<FavoritesResponse> addArtist(
            @Valid @RequestBody AddFavoriteRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            favoritesService.addArtist(authentication.getName(), payload.externalId()));
    }

    @DeleteMapping("/artists/{externalId}")
    @Operation(summary = "Usuwa artyste z ulubionych")
    public ResponseEntity<FavoritesResponse> removeArtist(
            @PathVariable String externalId,
            Authentication authentication) {

        return ResponseEntity.ok(
            favoritesService.removeArtist(authentication.getName(), externalId));
    }

    @PostMapping("/tracks")
    @Operation(summary = "Dodaje utwor z katalogu Deezera do ulubionych")
    public ResponseEntity<FavoritesResponse> addTrack(
            @Valid @RequestBody AddFavoriteRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            favoritesService.addTrack(authentication.getName(), payload.externalId()));
    }

    @DeleteMapping("/tracks/{externalId}")
    @Operation(summary = "Usuwa utwor z ulubionych")
    public ResponseEntity<FavoritesResponse> removeTrack(
            @PathVariable String externalId,
            Authentication authentication) {

        return ResponseEntity.ok(
            favoritesService.removeTrack(authentication.getName(), externalId));
    }

    @GetMapping("/import/lastfm")
    @Operation(summary = "Czy import z Last.fm jest wlaczony (czy jest klucz API)")
    public ResponseEntity<Map<String, Boolean>> isImportAvailable() {
        // Frontend chowa caly przycisk, gdy klucza nie ma - lepsze to niz
        // przycisk, ktory zawsze konczy sie bledem
        return ResponseEntity.ok(Map.of("available", favoritesService.importAvailable()));
    }

    @PostMapping("/import/lastfm")
    @Operation(summary = "Pobiera najczesciej sluchanych artystow i utwory z Last.fm")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Podsumowanie: co doszlo, co pominieto"),
        @ApiResponse(responseCode = "409",
            description = "Brak klucza API, nieznany uzytkownik Last.fm albo awaria serwisu")
    })
    public ResponseEntity<ImportSummary> importFromLastFm(
            @Valid @RequestBody ImportLastFmRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            favoritesService.importFromLastFm(authentication.getName(), payload.username()));
    }
}
