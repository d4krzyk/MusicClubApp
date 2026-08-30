package com.musicclubapp.controller;

import com.musicclubapp.dto.CommonGroundResponse;
import com.musicclubapp.dto.FriendCardResponse;
import com.musicclubapp.dto.PlaylistsResponse;
import com.musicclubapp.dto.PublicProfileResponse;
import com.musicclubapp.dto.TopMusicResponse;
import com.musicclubapp.dto.FavoritesResponse;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.service.CommonGroundService;
import com.musicclubapp.service.FavoritesService;
import com.musicclubapp.service.FriendService;
import com.musicclubapp.service.PlaylistService;
import com.musicclubapp.service.PublicProfileService;
import com.musicclubapp.service.TopMusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Publiczne profile uzytkownikow. */
@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profile", description = "Publiczne profile uzytkownikow")
public class PublicProfileController {

    private static final int MAX_SIZE = 50;

    private final PublicProfileService publicProfileService;
    private final FriendService friendService;
    private final TopMusicService topMusicService;
    private final FavoritesService favoritesService;
    private final PlaylistService playlistService;
    private final CommonGroundService commonGroundService;

    public PublicProfileController(PublicProfileService publicProfileService,
                                   FriendService friendService,
                                   TopMusicService topMusicService,
                                   FavoritesService favoritesService,
                                   PlaylistService playlistService,
                                   CommonGroundService commonGroundService) {
        this.favoritesService = favoritesService;
        this.publicProfileService = publicProfileService;
        this.friendService = friendService;
        this.topMusicService = topMusicService;
        this.playlistService = playlistService;
        this.commonGroundService = commonGroundService;
    }

    @GetMapping("/{username}")
    @Operation(summary = "Profil uzytkownika: awatar, data dolaczenia, liczba postow")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<PublicProfileResponse> profile(
            @PathVariable String username,
            Authentication authentication) {

        return ResponseEntity.ok(
            publicProfileService.profile(username, authentication.getName()));
    }

    /** Znajomi danej osoby - od najbardziej powiazanych z ogladajacym. */
    @GetMapping("/{username}/friends")
    @Operation(summary = "Znajomi uzytkownika, od najbardziej powiazanych z ogladajacym")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona znajomych"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<Page<FriendCardResponse>> friends(
            @PathVariable String username,

            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ilu znajomych na stronie (max 50)")
            @RequestParam(defaultValue = "6") int size,

            Authentication authentication) {

        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_SIZE));

        return ResponseEntity.ok(
            friendService.friends(username, authentication.getName(), pageable));
    }

    /** Najczesciej wrzucane przez uzytkownika nagrania - "top 5" na profilu. */
    @GetMapping("/{username}/top-music")
    @Operation(summary = "Najczesciej wrzucane przez uzytkownika nagrania")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista, od najczestszych"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<List<TopMusicResponse>> mostPosted(
            @PathVariable String username,

            @Parameter(description = "Rodzaj: TRACK, ALBUM albo ARTIST")
            @RequestParam(defaultValue = "TRACK") MusicKind kind,

            @Parameter(description = "Ile pozycji (1-20)")
            @RequestParam(defaultValue = "5") int limit) {

        return ResponseEntity.ok(topMusicService.mostPosted(username, kind, limit));
    }

    @GetMapping("/{username}/favorites")
    @Operation(summary = "Ulubieni artysci i utwory tej osoby")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ulubione"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<FavoritesResponse> favorites(
            @PathVariable String username,
            Authentication authentication) {

        /* Login ogladajacego idzie do serwisu, bo to on wylicza pole canEdit. */
        return ResponseEntity.ok(
            favoritesService.favorites(username, authentication.getName()));
    }

    /** Co laczy ogladajacego z ta osoba - konkretnie, a nie w liczbach. */
    @GetMapping("/{username}/common")
    @Operation(summary = "Wspolni artysci, utwory, gatunki i znajomi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Czesc wspolna gustow"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<CommonGroundResponse> commonGround(
            @PathVariable String username,
            Authentication authentication) {

        return ResponseEntity.ok(
            commonGroundService.between(authentication.getName(), username));
    }

    @GetMapping("/{username}/playlists")
    @Operation(summary = "Gablotka playlist tej osoby")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Do pieciu playlist"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<PlaylistsResponse> playlists(
            @PathVariable String username,
            Authentication authentication) {

        return ResponseEntity.ok(
            playlistService.playlists(username, authentication.getName()));
    }
}
