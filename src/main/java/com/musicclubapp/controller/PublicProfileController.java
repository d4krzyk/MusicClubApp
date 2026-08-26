package com.musicclubapp.controller;

import com.musicclubapp.dto.FriendCardResponse;
import com.musicclubapp.dto.PublicProfileResponse;
import com.musicclubapp.dto.TopMusicResponse;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.service.FriendService;
import com.musicclubapp.service.PublicProfileService;
import com.musicclubapp.service.TopMusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private static final int MAX_SIZE = 50;

    private final PublicProfileService publicProfileService;
    private final FriendService friendService;
    private final TopMusicService topMusicService;

    public PublicProfileController(PublicProfileService publicProfileService,
                                   FriendService friendService,
                                   TopMusicService topMusicService) {
        this.publicProfileService = publicProfileService;
        this.friendService = friendService;
        this.topMusicService = topMusicService;
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

    /**
     * Znajomi danej osoby - od najbardziej powiazanych z ogladajacym.
     *
     * <p>Lista jest tutaj, a nie w {@code /api/friends}, bo to informacja
     * O KIMS - tak samo jak jego posty. Pod {@code /api/friends} sa czynnosci
     * dotyczace wlasnych relacji zalogowanego uzytkownika.</p>
     *
     * <p>Stronicowana (wymagania nr 3 i 5): pasek na profilu pobiera kolejne
     * strony po kliknieciu strzalki, zamiast sciagac wszystkich naraz.</p>
     */
    @GetMapping("/{username}/friends")
    @Operation(summary = "Znajomi uzytkownika, od najbardziej powiazanych z ogladajacym")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona znajomych"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<Page<FriendCardResponse>> znajomi(
            @PathVariable String username,

            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ilu znajomych na stronie (max 50)")
            @RequestParam(defaultValue = "6") int size,

            Authentication authentication) {

        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_SIZE));

        return ResponseEntity.ok(
            friendService.znajomi(username, authentication.getName(), pageable));
    }

    /**
     * Najczesciej wrzucane przez uzytkownika nagrania - "top 5" na profilu.
     *
     * <p>Osobny endpoint, a nie pole w profilu: to zestawienie jest widgetem,
     * ktory da sie doladowac osobno, a sam profil pobieramy czesto i nie ma
     * sensu za kazdym razem ciagnac razem z nim zapytania grupujacego.</p>
     *
     * <p>Parametr {@code kind} pozwoli pozniej pokazac takze najczesciej
     * wrzucane albumy i artystow, bez dokladania kolejnych endpointow.</p>
     */
    @GetMapping("/{username}/top-music")
    @Operation(summary = "Najczesciej wrzucane przez uzytkownika nagrania")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista, od najczestszych"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<List<TopMusicResponse>> najczesciej(
            @PathVariable String username,

            @Parameter(description = "Rodzaj: TRACK, ALBUM albo ARTIST")
            @RequestParam(defaultValue = "TRACK") MusicKind kind,

            @Parameter(description = "Ile pozycji (1-20)")
            @RequestParam(defaultValue = "5") int limit) {

        return ResponseEntity.ok(topMusicService.najczesciej(username, kind, limit));
    }
}
