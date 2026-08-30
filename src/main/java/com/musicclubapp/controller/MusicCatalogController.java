package com.musicclubapp.controller;

import com.musicclubapp.dto.CatalogArtist;
import com.musicclubapp.dto.CatalogTrack;
import com.musicclubapp.service.DeezerCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Wyszukiwarka katalogu muzycznego - podpowiedzi przy dodawaniu ulubionych. */
@RestController
@RequestMapping("/api/music")
@Tag(name = "Katalog muzyczny", description = "Wyszukiwanie artystow i utworow (Deezer)")
public class MusicCatalogController {

    /** Domyslna liczba podpowiedzi - tyle miesci sie na liscie bez przewijania. */
    private static final int DOMYSLNY_LIMIT = 8;

    private final DeezerCatalogService catalog;

    public MusicCatalogController(DeezerCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search/artists")
    @Operation(summary = "Szuka artystow w katalogu Deezera")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Lista wynikow; PUSTA takze wtedy, gdy Deezer nie odpowiedzial")
    })
    public ResponseEntity<List<CatalogArtist>> artists(
            @RequestParam String q,
            @RequestParam(defaultValue = "" + DOMYSLNY_LIMIT) int limit) {

        return ResponseEntity.ok(catalog.searchArtists(q, limit));
    }

    @GetMapping("/search/tracks")
    @Operation(summary = "Szuka utworow w katalogu Deezera")
    public ResponseEntity<List<CatalogTrack>> tracks(
            @RequestParam String q,
            @RequestParam(defaultValue = "" + DOMYSLNY_LIMIT) int limit) {

        return ResponseEntity.ok(catalog.searchTracks(q, limit));
    }
}
