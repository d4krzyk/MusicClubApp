package com.musicclubapp.controller;

import com.musicclubapp.dto.CreatePostRequest;
import com.musicclubapp.dto.FeedScope;
import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.dto.UpdatePostRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Posty uzytkownikow: tekst, zdjecia i utwor ze Spotify. */
@RestController
@RequestMapping("/api/posts")
@Tag(name = "Posty", description = "Tablica i dodawanie postow")
public class PostController {

    private static final int MAX_SIZE = 50;

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** Tablica - najpierw posty znajomych, potem publiczne posty pozostalych. */
    @GetMapping
    @Operation(summary = "Tablica: najpierw znajomi, potem reszta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona postow"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Page<PostResponse>> feed(
            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile postow na stronie (max 50)")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Kierunek dla postow jednego autora: desc = najnowsze pierwsze")
            @RequestParam(defaultValue = "desc") String direction,

            @Parameter(description = "Login autora - gdy pusty, zwracamy tablice")
            @RequestParam(required = false) String author,

            @Parameter(description = "ALL = znajomi i reszta, FRIENDS = tylko krag znajomych")
            @RequestParam(defaultValue = "ALL") FeedScope scope,

            @Parameter(description = "Identyfikator wydarzenia - posty pod tym wydarzeniem")
            @RequestParam(required = false) Long event,

            Authentication authentication) {

        Sort sort = "asc".equalsIgnoreCase(direction)
            ? Sort.by("createdAt").ascending()
            : Sort.by("createdAt").descending();

        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_SIZE),
            sort);

        String username = authentication.getName();

        Page<PostResponse> result;
        if (event != null) {
            result = postService.byEvent(event, username, pageable);
        } else if (author == null || author.isBlank()) {
            result = postService.feed(username, scope, pageable);
        } else {
            result = postService.byAuthor(author, username, pageable);
        }

        return ResponseEntity.ok(result);
    }

    /** Same liczniki reakcji dla wskazanych postow. */
    @GetMapping("/reactions")
    @Operation(summary = "Liczniki reakcji dla wskazanych postow")
    public ResponseEntity<Map<Long, ReactionSummary>> reactions(
            @Parameter(description = "Identyfikatory postow, po przecinku")
            @RequestParam List<Long> ids,
            Authentication authentication) {

        // Gorny limit taki sam jak przy stronie postow - i tak wiecej naraz
        // nigdy nie ma na ekranie
        List<Long> limited = ids.stream().limit(MAX_SIZE).toList();

        return ResponseEntity.ok(
            postService.reactionSummaries(limited, authentication.getName()));
    }

    /** Jeden post po identyfikatorze. */
    @GetMapping("/{id}")
    @Operation(summary = "Zwraca jeden post")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego posta")
    })
    public ResponseEntity<PostResponse> getOne(@PathVariable Long id,
                                               Authentication authentication) {
        return ResponseEntity.ok(postService.getOne(id, authentication.getName()));
    }

    /** Dodanie posta. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Dodaje post z tekstem, zdjeciami i utworem ze Spotify")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Post dodany"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji albo niedozwolony plik")
    })
    public ResponseEntity<PostResponse> create(
            @Valid @RequestPart("post") CreatePostRequest payload,

            @Parameter(description = "Zdjecia - opcjonalne, maksymalnie 10")
            @RequestPart(value = "images", required = false) List<MultipartFile> images,

            Authentication authentication) {

        PostResponse created = postService.create(authentication.getName(), payload, images);

        URI location = UriComponentsBuilder.fromPath("/api/posts/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /** Edycja wlasnego posta. */
    @PutMapping("/{id}")
    @Operation(summary = "Edytuje wlasny post (tresc i utwor ze Spotify)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post zapisany"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego posta"),
        @ApiResponse(responseCode = "409", description = "Proba edycji cudzego posta"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji")
    })
    public ResponseEntity<PostResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(postService.update(id, authentication.getName(), payload));
    }

    /**
     * Usuniecie wlasnego posta (administrator moze usunac dowolny). 204 NO CONTENT - udalo sie,
     * ale nie ma czego zwracac.
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
