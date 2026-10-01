package com.musicclubapp.controller;

import com.musicclubapp.dto.ClanPollRequest;
import com.musicclubapp.dto.ClanPollResponse;
import com.musicclubapp.dto.ClanPollVoteRequest;
import com.musicclubapp.service.ClanPollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ankiety w klanie. */
@RestController
@RequestMapping("/api/clans/{id}/polls")
@Tag(name = "Klany", description = "Przegladarka klanow, prosby o dolaczenie, tytuly, ankiety i ranking")
public class ClanPollController {

    private final ClanPollService polls;

    public ClanPollController(ClanPollService polls) {
        this.polls = polls;
    }

    @GetMapping
    @Operation(summary = "Ankiety klanu: otwarte i ostatnio zamkniete")
    public ResponseEntity<List<ClanPollResponse>> list(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(polls.list(id, auth.getName()));
    }

    @PostMapping
    @Operation(summary = "Zaklada ankiete (czlonek)")
    public ResponseEntity<ClanPollResponse> create(@PathVariable Long id, @Valid @RequestBody ClanPollRequest payload,
                                                   Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(polls.create(id, auth.getName(), payload));
    }

    @PutMapping("/{pollId}/vote")
    @Operation(summary = "Glosuje albo zmienia glos w otwartej ankiecie")
    public ResponseEntity<Void> vote(@PathVariable Long id, @PathVariable Long pollId,
                                     @Valid @RequestBody ClanPollVoteRequest payload, Authentication auth) {
        polls.vote(id, pollId, auth.getName(), payload.optionId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{pollId}/vote")
    @Operation(summary = "Cofa glos w otwartej ankiecie")
    public ResponseEntity<Void> unvote(@PathVariable Long id, @PathVariable Long pollId, Authentication auth) {
        polls.unvote(id, pollId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{pollId}/close")
    @Operation(summary = "Zamyka ankiete przed terminem (autor albo zarzad klanu)")
    public ResponseEntity<Void> close(@PathVariable Long id, @PathVariable Long pollId, Authentication auth) {
        polls.close(id, pollId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{pollId}")
    @Operation(summary = "Usuwa ankiete (autor albo zarzad klanu)")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long pollId, Authentication auth) {
        polls.delete(id, pollId, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
