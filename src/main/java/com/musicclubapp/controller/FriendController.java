package com.musicclubapp.controller;

import com.musicclubapp.dto.CreateFriendRequest;
import com.musicclubapp.dto.PendingRequestsResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Znajomi: zaproszenia i usuwanie znajomosci.
 *
 * <p>Sama LISTA znajomych wisi pod profilem
 * ({@code GET /api/profiles/{username}/friends}), bo to informacja o kims -
 * tak samo jak jego posty. Tutaj sa czynnosci dotyczace MOICH relacji.</p>
 */
@RestController
@RequestMapping("/api/friends")
@Tag(name = "Znajomi", description = "Zaproszenia i lista znajomych")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping("/requests")
    @Operation(summary = "Zaproszenia oczekujace: przychodzace i wyslane")
    public ResponseEntity<PendingRequestsResponse> oczekujace(Authentication authentication) {
        return ResponseEntity.ok(friendService.oczekujace(authentication.getName()));
    }

    @GetMapping("/requests/count")
    @Operation(summary = "Ile zaproszen czeka na moja odpowiedz (liczba w menu)")
    public ResponseEntity<Map<String, Long>> ile(Authentication authentication) {
        return ResponseEntity.ok(
            Map.of("count", friendService.ileOczekujacych(authentication.getName())));
    }

    /**
     * Wysyla zaproszenie.
     *
     * <p>Odpowiedz mowi, czy znajomosc powstala OD RAZU - dzieje sie tak, gdy
     * druga osoba wczesniej zaprosila nas. Frontend dzieki temu od razu
     * pokazuje "Znajomi" zamiast "Zaproszenie wyslane".</p>
     */
    @PostMapping("/requests")
    @Operation(summary = "Zaprasza uzytkownika do znajomych")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Zaproszenie wyslane"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409",
            description = "Zaproszenie do siebie, juz sa znajomymi albo zaproszenie juz czeka")
    })
    public ResponseEntity<Map<String, Boolean>> zapros(
            @Valid @RequestBody CreateFriendRequest zadanie,
            Authentication authentication) {

        boolean odRazuZnajomi = friendService.zapros(
            authentication.getName(), zadanie.username());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("friendsNow", odRazuZnajomi));
    }

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Przyjmuje zaproszenie skierowane do mnie")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Przyjete - jestescie znajomymi"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego zaproszenia"),
        @ApiResponse(responseCode = "409", description = "To nie jest zaproszenie do mnie")
    })
    public ResponseEntity<Void> przyjmij(@PathVariable Long id, Authentication authentication) {
        friendService.przyjmij(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Odrzuca zaproszenie do mnie albo anuluje moje wlasne.
     *
     * <p>Jeden endpoint na oba przypadki, bo w obu chodzi o skasowanie tego
     * samego wiersza. Kto stoi po ktorej stronie, serwer sprawdza sam.</p>
     */
    @DeleteMapping("/requests/{id}")
    @Operation(summary = "Odrzuca zaproszenie do mnie albo anuluje wyslane przeze mnie")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zaproszenie usuniete"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego zaproszenia"),
        @ApiResponse(responseCode = "409", description = "To nie jest Twoje zaproszenie")
    })
    public ResponseEntity<Void> odrzuc(@PathVariable Long id, Authentication authentication) {
        friendService.odrzucLubAnuluj(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "Usuwa znajomosc (u obu osob naraz)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Znajomosc usunieta"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<Void> usun(@PathVariable String username,
                                     Authentication authentication) {
        friendService.usunZnajomego(authentication.getName(), username);
        return ResponseEntity.noContent().build();
    }
}
