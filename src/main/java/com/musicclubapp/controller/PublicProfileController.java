package com.musicclubapp.controller;

import com.musicclubapp.dto.PublicProfileResponse;
import com.musicclubapp.service.PublicProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publiczne profile uzytkownikow.
 *
 * <p><b>Uwaga na dwie podobne sciezki</b> - latwo je pomylic:</p>
 * <ul>
 *   <li>{@code /api/profile} (l. poj.) - MOJE konto: zmiana loginu, e-maila,
 *       hasla, awatara. Dotyczy wylacznie zalogowanego uzytkownika.</li>
 *   <li>{@code /api/profiles/{username}} (l. mn.) - CZYJS profil do ogladania.
 *       Tylko odczyt i tylko dane jawne.</li>
 * </ul>
 *
 * <p>Osobno od {@code /api/users/**}, ktore jest zarezerwowane dla
 * administratora i zwraca m.in. adresy e-mail oraz role. Gdyby profil
 * publiczny wisial pod tamta sciezka, musialby albo zlamac te regule,
 * albo dorobic wyjatek w konfiguracji bezpieczenstwa - a wyjatki
 * w regulach dostepu to najlatwiejszy sposob na przypadkowa dziure.</p>
 */
@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profile", description = "Publiczne profile uzytkownikow")
public class PublicProfileController {

    private final PublicProfileService publicProfileService;

    public PublicProfileController(PublicProfileService publicProfileService) {
        this.publicProfileService = publicProfileService;
    }

    @GetMapping("/{username}")
    @Operation(summary = "Profil uzytkownika: awatar, data dolaczenia, liczba postow")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<PublicProfileResponse> profil(
            @PathVariable String username,
            Authentication authentication) {

        return ResponseEntity.ok(
            publicProfileService.profil(username, authentication.getName()));
    }
}
