package com.musicclubapp.controller;

import com.musicclubapp.dto.CreateFriendRequest;
import com.musicclubapp.dto.PendingRequestsResponse;
import com.musicclubapp.dto.SuggestionResponse;
import com.musicclubapp.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Znajomi: zaproszenia i usuwanie znajomosci. */
@RestController
@RequestMapping("/api/friends")
@Tag(name = "Znajomi", description = "Zaproszenia i lista znajomych")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /** Proponowani znajomi - cala spolecznosc, od najlepiej dopasowanych. */
    @GetMapping("/suggestions")
    @Operation(summary = "Proponowani znajomi: wszyscy uzytkownicy, od najlepiej dopasowanych")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Lista kart wraz z powodem dopasowania"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<List<SuggestionResponse>> suggestions(
            @RequestParam(defaultValue = "24") int limit,
            Authentication authentication) {

        return ResponseEntity.ok(
            friendService.suggestions(authentication.getName(), limit));
    }

    @GetMapping("/requests")
    @Operation(summary = "Zaproszenia oczekujace: przychodzace i wyslane")
    public ResponseEntity<PendingRequestsResponse> pending(Authentication authentication) {
        return ResponseEntity.ok(friendService.pending(authentication.getName()));
    }

    @GetMapping("/requests/count")
    @Operation(summary = "Ile zaproszen czeka na moja odpowiedz (liczba w menu)")
    public ResponseEntity<Map<String, Long>> count(Authentication authentication) {
        return ResponseEntity.ok(
            Map.of("count", friendService.countPending(authentication.getName())));
    }

    /** Wysyla zaproszenie. */
    @PostMapping("/requests")
    @Operation(summary = "Zaprasza uzytkownika do znajomych")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Zaproszenie wyslane"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409",
            description = "Zaproszenie do siebie, juz sa znajomymi albo zaproszenie juz czeka")
    })
    public ResponseEntity<Map<String, Boolean>> invite(
            @Valid @RequestBody CreateFriendRequest payload,
            Authentication authentication) {

        boolean instantFriends = friendService.invite(
            authentication.getName(), payload.username());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("friendsNow", instantFriends));
    }

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Przyjmuje zaproszenie skierowane do mnie")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Przyjete - jestescie znajomymi"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego zaproszenia"),
        @ApiResponse(responseCode = "409", description = "To nie jest zaproszenie do mnie")
    })
    public ResponseEntity<Void> accept(@PathVariable Long id, Authentication authentication) {
        friendService.accept(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** Odrzuca zaproszenie do mnie albo anuluje moje wlasne. */
    @DeleteMapping("/requests/{id}")
    @Operation(summary = "Odrzuca zaproszenie do mnie albo anuluje wyslane przeze mnie")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zaproszenie usuniete"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego zaproszenia"),
        @ApiResponse(responseCode = "409", description = "To nie jest Twoje zaproszenie")
    })
    public ResponseEntity<Void> reject(@PathVariable Long id, Authentication authentication) {
        friendService.rejectOrCancel(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "Usuwa znajomosc (u obu osob naraz)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Znajomosc usunieta"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<Void> remove(@PathVariable String username,
                                     Authentication authentication) {
        friendService.removeFriend(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }
}
