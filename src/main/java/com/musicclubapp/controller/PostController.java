package com.musicclubapp.controller;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Posty uzytkownikow: tekst, zdjecia i utwor ze Spotify.
 *
 * <p><b>Dlaczego {@code multipart/form-data}, a nie zwykly JSON?</b> Bo razem
 * z tekstem lecą pliki, a JSON nie przenosi plikow binarnych. Zapytanie sklada
 * sie wiec z dwoch czesci: {@code post} (JSON z trescia) i {@code images}
 * (wgrane obrazki) - stad adnotacja {@code @RequestPart} zamiast
 * {@code @RequestBody}.</p>
 */
@RestController
@RequestMapping("/api/posts")
@Tag(name = "Posty", description = "Tablica i dodawanie postow")
public class PostController {

    private static final int MAX_SIZE = 50;

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * Tablica - posty wszystkich uzytkownikow, od najnowszych.
     * Stronicowanie i sortowanie po stronie backendu (wymagania nr 3 i 5).
     */
    @GetMapping
    @Operation(summary = "Tablica postow, od najnowszych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona postow"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Page<PostResponse>> feed(
            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile postow na stronie (max 50)")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Kierunek: desc = najnowsze pierwsze")
            @RequestParam(defaultValue = "desc") String direction,

            @Parameter(description = "Login autora - gdy pusty, zwracamy posty wszystkich")
            @RequestParam(required = false) String author,

            Authentication authentication) {

        Sort sort = "asc".equalsIgnoreCase(direction)
            ? Sort.by("createdAt").ascending()
            : Sort.by("createdAt").descending();

        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_SIZE),
            sort);

        String login = authentication.getName();

        Page<PostResponse> wynik = (author == null || author.isBlank())
            ? postService.feed(login, pageable)
            : postService.byAuthor(author, login, pageable);

        return ResponseEntity.ok(wynik);
    }

    /**
     * Dodanie posta.
     *
     * <p>Zwracamy 201 CREATED z naglowkiem {@code Location} - wyklad 4,
     * slajd 32.</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Dodaje post z tekstem, zdjeciami i utworem ze Spotify")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Post dodany"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji albo niedozwolony plik")
    })
    public ResponseEntity<PostResponse> create(
            @Valid @RequestPart("post") CreatePostRequest zadanie,

            @Parameter(description = "Zdjecia - opcjonalne, maksymalnie 10")
            @RequestPart(value = "images", required = false) List<MultipartFile> zdjecia,

            Authentication authentication) {

        PostResponse utworzony = postService.create(authentication.getName(), zadanie, zdjecia);

        URI location = UriComponentsBuilder.fromPath("/api/posts/{id}")
            .buildAndExpand(utworzony.id())
            .toUri();

        return ResponseEntity.created(location).body(utworzony);
    }

    /**
     * Usuniecie wlasnego posta (administrator moze usunac dowolny).
     *
     * <p>204 NO CONTENT - udalo sie, ale nie ma czego zwracac.</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Usuwa post (autor albo administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Post usuniety"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego posta"),
        @ApiResponse(responseCode = "409", description = "Proba usuniecia cudzego posta")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        postService.delete(id, authentication.getName());

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
