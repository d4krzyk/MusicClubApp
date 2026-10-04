package com.musicclubapp.controller;

import com.musicclubapp.dto.DiscoverCard;
import com.musicclubapp.dto.DiscoverDeckResponse;
import com.musicclubapp.dto.DiscoverSettingsRequest;
import com.musicclubapp.dto.DiscoverStatusResponse;
import com.musicclubapp.dto.SwipeRequest;
import com.musicclubapp.dto.SwipeResponse;
import com.musicclubapp.service.DiscoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tryb Poznawaj: karty osob z okolicy, w prawo = chce poznac, wzajemne "tak" = znajomi. */
@RestController
@RequestMapping("/api/discover")
@Tag(name = "Poznawaj", description = "Poznawanie ludzi w stylu kart: wzajemne \"tak\" = znajomosc")
public class DiscoverController {

    private final DiscoverService discover;

    public DiscoverController(DiscoverService discover) {
        this.discover = discover;
    }

    @GetMapping("/me")
    @Operation(summary = "Czy mam wlaczony tryb, zasieg, braki na karcie i podglad mojej karty")
    public ResponseEntity<DiscoverStatusResponse> me(Authentication auth) {
        return ResponseEntity.ok(discover.status(auth.getName()));
    }

    @PutMapping("/settings")
    @Operation(summary = "Wlacza albo wylacza tryb Poznawaj i ustawia zasieg (km; 0 = caly kraj)")
    public ResponseEntity<DiscoverStatusResponse> settings(@RequestBody DiscoverSettingsRequest payload,
                                                           Authentication auth) {
        return ResponseEntity.ok(discover.settings(auth.getName(), payload));
    }

    @GetMapping("/deck")
    @Operation(summary = "Kolejne karty - od najlepiej dopasowanych gustem; skip = loginy kart, ktore juz mam")
    public ResponseEntity<DiscoverDeckResponse> deck(
            @RequestParam(defaultValue = "" + DiscoverService.TALIA_DOMYSLNIE) int limit,
            @RequestParam(required = false) List<String> skip,
            Authentication auth) {
        return ResponseEntity.ok(discover.deck(auth.getName(), limit, skip));
    }

    @PostMapping("/swipes")
    @Operation(summary = "Decyzja o osobie z talii: LIKE (w prawo) albo PASS (w lewo)")
    public ResponseEntity<SwipeResponse> swipe(@Valid @RequestBody SwipeRequest payload, Authentication auth) {
        return ResponseEntity.ok(discover.swipe(auth.getName(), payload));
    }

    @PostMapping("/undo")
    @Operation(summary = "Cofa ostatnia decyzje (z ostatnich 10 minut) i oddaje karte tej osoby")
    public ResponseEntity<DiscoverCard> undo(Authentication auth) {
        return ResponseEntity.ok(discover.undo(auth.getName()));
    }
}
