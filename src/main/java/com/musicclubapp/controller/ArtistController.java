package com.musicclubapp.controller;

import com.musicclubapp.dto.ArtistProfileResponse;
import com.musicclubapp.service.ArtistProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Kim jest" wykonawca z koncertu - opis z Last.fm i linki. */
@RestController
@RequestMapping("/api/artists")
@Tag(name = "Wykonawcy", description = "Opisy wykonawcow z koncertow")
public class ArtistController {

    private final ArtistProfileService profiles;

    public ArtistController(ArtistProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    @Operation(summary = "Opis wykonawcy (tylko ktos, kto gra na jakims wydarzeniu); lang = pl albo en")
    public ResponseEntity<ArtistProfileResponse> profile(@RequestParam String name,
                                                         @RequestParam(required = false) String lang,
                                                         Authentication authentication) {
        return ResponseEntity.ok(profiles.profile(name, lang, authentication.getName()));
    }
}
