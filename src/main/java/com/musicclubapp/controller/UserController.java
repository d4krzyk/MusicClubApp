package com.musicclubapp.controller;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.BlockIpRequest;
import com.musicclubapp.dto.BlockedIpResponse;
import com.musicclubapp.dto.RelatedAccountResponse;
import com.musicclubapp.dto.ChangeRoleRequest;
import com.musicclubapp.dto.BanRequest;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.service.UserModerationService;
import com.musicclubapp.service.NetworkService;
import com.musicclubapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Przegladanie uzytkownikow - stronicowanie i sortowanie po stronie backendu. */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Uzytkownicy", description = "Przegladanie i wyszukiwanie uzytkownikow")
public class UserController {

    /** Zabezpieczenie przed {@code ?size=1000000}, ktore polozyloby serwer. */
    private static final int MAX_SIZE = 100;

    private final UserService userService;
    private final UserModerationService moderationService;
    private final NetworkService network;

    public UserController(UserService userService,
                          UserModerationService moderationService,
                          NetworkService network) {
        this.userService = userService;
        this.moderationService = moderationService;
        this.network = network;
    }

    @GetMapping
    @Operation(summary = "Lista uzytkownikow ze stronicowaniem i sortowaniem")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Strona wynikow"),
        @ApiResponse(responseCode = "401", description = "Wymagane zalogowanie")
    })
    public ResponseEntity<Page<AdminUserResponse>> search(
            @Parameter(description = "Fragment loginu lub e-maila")
            @RequestParam(defaultValue = "") String fragment,

            @Parameter(description = "Numer strony, liczony od zera")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Ile elementow na stronie (max 100)")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Pole do sortowania, np. username albo createdAt")
            @RequestParam(defaultValue = "username") String sortBy,

            @Parameter(description = "Kierunek sortowania: asc albo desc")
            @RequestParam(defaultValue = "asc") String direction) {

        // Sort budowany budowniczym - wyklad 3, slajd 42
        Sort sort = "desc".equalsIgnoreCase(direction)
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

        // Pageable laczy stronicowanie z sortowaniem - wyklad 3, slajd 43
        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_SIZE),
            sort);

        return ResponseEntity.ok(userService.search(fragment, pageable));
    }

    /** Pojedynczy uzytkownik po ID. */
    @GetMapping("/{id}")
    @Operation(summary = "Zwraca uzytkownika o podanym ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane uzytkownika"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<AdminUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getByIdForAdmin(id));
    }

    /** Zmiana roli innego uzytkownika. */
    @PatchMapping("/{id}/role")
    @Operation(summary = "Zmienia role uzytkownika (tylko administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rola zmieniona"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnien administratora"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409", description = "Proba zmiany wlasnej roli")
    })
    public ResponseEntity<AdminUserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            userService.changeRole(authentication.getName(), id, payload));
    }

    /** Naklada albo zdejmuje kare - jeden adres na oba rodzaje. */
    @PatchMapping("/{id}/bans/{kind}")
    @Operation(summary = "Naklada albo zdejmuje kare na koncie (tylko administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kara nalozona albo zdjeta"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnien administratora"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409", description = "Proba ukarania samego siebie"),
        @ApiResponse(responseCode = "422", description = "Liczba godzin poza zakresem 1-8760")
    })
    public ResponseEntity<AdminUserResponse> setBan(
            @PathVariable Long id,
            @PathVariable BanKind kind,
            @Valid @RequestBody BanRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            moderationService.setBan(authentication.getName(), id, kind, payload));
    }

    /** Usuwa konto razem z jego postami, reakcjami, znajomosciami i plikami. */
    @DeleteMapping("/{id}")
    @Operation(summary = "Usuwa konto uzytkownika (tylko administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Konto usuniete"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnien administratora"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409", description = "Proba usuniecia wlasnego konta")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        moderationService.deleteUser(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /** Konta logujace sie z tych samych adresow co wskazane - poszlaka multikonta. */
    @GetMapping("/{id}/related")
    @Operation(summary = "Konta z tego samego adresu sieciowego (tylko administrator)")
    public ResponseEntity<List<RelatedAccountResponse>> related(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.relatedAccounts(id));
    }

    /** Adresy, z ktorych logowalo sie to konto - do skopiowania w blokade. */
    @GetMapping("/{id}/addresses")
    @Operation(summary = "Adresy logowan tego konta (tylko administrator)")
    public ResponseEntity<List<RelatedAccountResponse>> addresses(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.addressesOf(id));
    }

    /* ------------------------------------------------------------------ */
    /*  Zablokowane adresy                                                 */
    /* ------------------------------------------------------------------ */

    @GetMapping("/blocked-ips")
    @Operation(summary = "Lista zablokowanych adresow (tylko administrator)")
    public ResponseEntity<List<BlockedIpResponse>> blockedIps() {
        return ResponseEntity.ok(network.blockedAddresses().stream()
            .map(blocked -> new BlockedIpResponse(
                blocked.getId(), blocked.getAddress(), blocked.getReason(),
                blocked.getBlockedBy(), blocked.getCreatedAt()))
            .toList());
    }

    /** Blokuje adres sieciowy. */
    @PostMapping("/blocked-ips")
    @Operation(summary = "Blokuje adres sieciowy (tylko administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Adres zablokowany"),
        @ApiResponse(responseCode = "409", description = "Proba zablokowania wlasnego adresu"),
        @ApiResponse(responseCode = "422", description = "Brak adresu albo powodu")
    })
    public ResponseEntity<BlockedIpResponse> blockIp(
            @Valid @RequestBody BlockIpRequest payload,
            HttpServletRequest http,
            Authentication authentication) {

        var blocked = network.block(
            authentication.getName(),
            network.clientIp(http),
            payload.address().trim(),
            payload.reason().trim());

        return ResponseEntity.status(HttpStatus.CREATED).body(new BlockedIpResponse(
            blocked.getId(), blocked.getAddress(), blocked.getReason(),
            blocked.getBlockedBy(), blocked.getCreatedAt()));
    }

    @DeleteMapping("/blocked-ips/{id}")
    @Operation(summary = "Zdejmuje blokade adresu (tylko administrator)")
    public ResponseEntity<Void> unblockIp(@PathVariable Long id) {
        network.unblock(id);
        return ResponseEntity.noContent().build();
    }
}
