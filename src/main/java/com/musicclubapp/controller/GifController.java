package com.musicclubapp.controller;

import com.musicclubapp.dto.GifPageResponse;
import com.musicclubapp.dto.GifStatusResponse;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.gif.GifService;
import com.musicclubapp.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Przegladarka GIF-ow dla pola komentarza i czatu. */
@RestController
@RequestMapping("/api/gifs")
@Tag(name = "GIF-y")
public class GifController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 30;

    private final GifService gifs;
    private final UserRepository users;

    public GifController(GifService gifs, UserRepository users) {
        this.gifs = gifs;
        this.users = users;
    }

    @GetMapping("/status")
    @Operation(summary = "Czy GIF-y sa wlaczone i kogo podpisac pod przegladarka")
    public ResponseEntity<GifStatusResponse> status() {
        return ResponseEntity.ok(new GifStatusResponse(gifs.enabled(), gifs.attribution()));
    }

    @GetMapping("/search")
    @Operation(summary = "Szuka GIF-ow u dostawcy (pusta fraza = popularne); wyniki sa podpisane")
    public ResponseEntity<GifPageResponse> search(Authentication authentication,
                                                  @RequestParam(name = "q", defaultValue = "") String q,
                                                  @RequestParam(name = "pos", required = false) String pos,
                                                  @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        Long userId = users.findByUsername(authentication.getName())
            .orElseThrow(() -> new NoSuchElementFoundException("user", authentication.getName())).getId();
        int clamped = Math.max(1, Math.min(MAX_LIMIT, limit));
        return ResponseEntity.ok(gifs.search(userId, q, pos, clamped, LocaleContextHolder.getLocale().getLanguage()));
    }
}
