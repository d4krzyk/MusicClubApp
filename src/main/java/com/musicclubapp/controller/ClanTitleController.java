package com.musicclubapp.controller;

import com.musicclubapp.dto.ClanResponse;
import com.musicclubapp.dto.ClanTitleRequest;
import com.musicclubapp.service.ClanTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tytuly (role) w klanie. */
@RestController
@RequestMapping("/api/clans/{id}")
@Tag(name = "Klany", description = "Przegladarka klanow, prosby o dolaczenie, tytuly, ankiety i ranking")
public class ClanTitleController {

    private final ClanTitleService titles;

    public ClanTitleController(ClanTitleService titles) {
        this.titles = titles;
    }

    @PostMapping("/titles")
    @Operation(summary = "Dodaje tytul do klanu (zarzad)")
    public ResponseEntity<ClanResponse> create(@PathVariable Long id, @Valid @RequestBody ClanTitleRequest payload,
                                               Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(titles.create(id, auth.getName(), payload));
    }

    @PutMapping("/titles/{titleId}")
    @Operation(summary = "Zmienia tytul (zarzad)")
    public ResponseEntity<ClanResponse> update(@PathVariable Long id, @PathVariable Long titleId,
                                               @Valid @RequestBody ClanTitleRequest payload, Authentication auth) {
        return ResponseEntity.ok(titles.update(id, titleId, auth.getName(), payload));
    }

    @DeleteMapping("/titles/{titleId}")
    @Operation(summary = "Usuwa tytul razem z jego posiadaczami (zarzad)")
    public ResponseEntity<ClanResponse> delete(@PathVariable Long id, @PathVariable Long titleId,
                                               Authentication auth) {
        return ResponseEntity.ok(titles.delete(id, titleId, auth.getName()));
    }

    @PutMapping("/members/{username}/titles/{titleId}")
    @Operation(summary = "Nadaje tytul czlonkowi (zarzad)")
    public ResponseEntity<ClanResponse> assign(@PathVariable Long id, @PathVariable String username,
                                               @PathVariable Long titleId, Authentication auth) {
        return ResponseEntity.ok(titles.assign(id, titleId, auth.getName(), username));
    }

    @DeleteMapping("/members/{username}/titles/{titleId}")
    @Operation(summary = "Zdejmuje tytul czlonkowi (zarzad; osoba - swoj tytul do wziecia samemu)")
    public ResponseEntity<ClanResponse> unassign(@PathVariable Long id, @PathVariable String username,
                                                 @PathVariable Long titleId, Authentication auth) {
        return ResponseEntity.ok(titles.unassign(id, titleId, auth.getName(), username));
    }

    @PutMapping("/titles/{titleId}/claim")
    @Operation(summary = "Bierze sobie tytul, ktory jest do wziecia samemu")
    public ResponseEntity<ClanResponse> claim(@PathVariable Long id, @PathVariable Long titleId,
                                              Authentication auth) {
        return ResponseEntity.ok(titles.claim(id, titleId, auth.getName()));
    }

    @DeleteMapping("/titles/{titleId}/claim")
    @Operation(summary = "Oddaje tytul wziety samemu")
    public ResponseEntity<ClanResponse> unclaim(@PathVariable Long id, @PathVariable Long titleId,
                                                Authentication auth) {
        return ResponseEntity.ok(titles.unclaim(id, titleId, auth.getName()));
    }
}
