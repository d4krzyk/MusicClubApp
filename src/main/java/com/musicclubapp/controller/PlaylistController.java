package com.musicclubapp.controller;

import com.musicclubapp.dto.AddPlaylistRequest;
import com.musicclubapp.dto.PlaylistsResponse;
import com.musicclubapp.service.PlaylistService;
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

/**
 * Gablotka playlist <b>zalogowanego uzytkownika</b>.
 *
 * <p>Ta sama zasada co przy ulubionych: {@code /api/profile/...} (liczba
 * pojedyncza) dotyczy MOICH danych i zawsze bierze login z sesji, nigdy ze
 * sciezki. Nie trzeba wiec sprawdzac, czy ktos nie podal cudzego loginu -
 * takiego parametru po prostu nie ma.</p>
 *
 * <p>CUDZA gablotka jest do odczytu pod
 * {@code /api/profiles/{username}/playlists} (liczba mnoga).</p>
 */
@RestController
@RequestMapping("/api/profile/playlists")
@Tag(name = "Playlisty", description = "Gablotka playlist na profilu")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping
    @Operation(summary = "Moja gablotka playlist")
    public ResponseEntity<PlaylistsResponse> mine(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(playlistService.playlists(username, username));
    }

    @PostMapping
    @Operation(summary = "Dodaje playliste do gablotki (Spotify, YouTube Music, Apple Music)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gablotka po zmianie"),
        @ApiResponse(responseCode = "409",
            description = "Gablotka pelna, adres nie jest playlista albo juz ja masz"),
        @ApiResponse(responseCode = "422", description = "Pusty adres")
    })
    public ResponseEntity<PlaylistsResponse> add(
            @Valid @RequestBody AddPlaylistRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            playlistService.add(authentication.getName(), payload.url()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usuwa playliste z gablotki")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gablotka po zmianie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiej playlisty"),
        @ApiResponse(responseCode = "409", description = "To nie jest Twoja playlista")
    })
    public ResponseEntity<PlaylistsResponse> remove(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(playlistService.remove(authentication.getName(), id));
    }
}
