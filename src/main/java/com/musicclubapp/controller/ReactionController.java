package com.musicclubapp.controller;

import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionRequest;
import com.musicclubapp.service.ReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reakcje na posty.
 *
 * <p><b>Dlaczego {@code PUT}, a nie {@code POST}?</b> {@code PUT} oznacza
 * "ustaw stan na taki", a nie "dolóz kolejny". Uzytkownik ma na dany post
 * dokladnie jedna reakcje, wiec wyslanie tego samego zadania dwa razy powinno
 * dac ten sam wynik - i daje. {@code POST} sugerowalby, ze za kazdym razem
 * powstaje nowy zasob.</p>
 *
 * <p>Obie metody zwracaja <b>caly zaktualizowany post</b>, a nie same liczniki.
 * Dzieki temu po kliknieciu przegladarka podmienia jeden wpis w tablicy
 * i ma pewnosc, ze widzi dokladnie to, co jest w bazie - zamiast dodawac
 * sobie {@code +1} na wlasna reke i rozjezdzac sie z serwerem.</p>
 */
@RestController
@RequestMapping("/api/posts/{postId}/reaction")
@Tag(name = "Reakcje", description = "Ogien, mid i meh pod postami")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PutMapping
    @Operation(summary = "Ustawia (albo podmienia) reakcje zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post z przeliczonymi licznikami"),
        @ApiResponse(responseCode = "400", description = "Nieznany rodzaj reakcji"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego posta")
    })
    public ResponseEntity<PostResponse> ustaw(
            @PathVariable Long postId,
            @Valid @RequestBody ReactionRequest zadanie,
            Authentication authentication) {

        return ResponseEntity.ok(
            reactionService.ustaw(postId, authentication.getName(), zadanie.type()));
    }

    @DeleteMapping
    @Operation(summary = "Cofa wlasna reakcje")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Post z przeliczonymi licznikami"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego posta")
    })
    public ResponseEntity<PostResponse> cofnij(
            @PathVariable Long postId,
            Authentication authentication) {

        return ResponseEntity.ok(reactionService.cofnij(postId, authentication.getName()));
    }
}
