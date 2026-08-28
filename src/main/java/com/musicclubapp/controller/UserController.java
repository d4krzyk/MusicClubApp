package com.musicclubapp.controller;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.BlockIpRequest;
import com.musicclubapp.dto.BlockedIpResponse;
import com.musicclubapp.dto.MessagingBanRequest;
import com.musicclubapp.dto.RelatedAccountResponse;
import com.musicclubapp.dto.ChangeRoleRequest;
import com.musicclubapp.dto.PostingBanRequest;
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

/**
 * Przegladanie uzytkownikow - stronicowanie i sortowanie po stronie backendu.
 *
 * <p>Realizuje wymagania nr 3 (stronicowanie + wybor liczby elementow),
 * nr 5 (sortowanie) i nr 22 ({@link ResponseEntity}).</p>
 *
 * <p>Parametry przyjmujemy przez {@code @RequestParam} z wartosciami
 * domyslnymi - dokladnie jak mowi wyklad 3, slajd 42: "Zazwyczaj robi sie to
 * poprzez {@code @RequestParam} z ustawieniem domyslnych wartosci".
 * Obiekty {@link Sort} i {@link Pageable} skladamy recznie (slajdy 38 i 43),
 * zeby bylo widac, skad sie biora.</p>
 *
 * <p>Przyklad zapytania:</p>
 * <pre>GET /api/users?fragment=an&amp;page=0&amp;size=10&amp;sortBy=createdAt&amp;direction=desc</pre>
 */
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

    /**
     * Pojedynczy uzytkownik po ID.
     *
     * <p>Gdy takiego nie ma, serwis rzuca {@code NoSuchElementFoundException},
     * a {@code GlobalExceptionHandler} zamienia to na 404 - wymaganie nr 11.</p>
     */
    @GetMapping("/{id}")
    @Operation(summary = "Zwraca uzytkownika o podanym ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane uzytkownika"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika")
    })
    public ResponseEntity<AdminUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getByIdForAdmin(id));
    }

    /**
     * Zmiana roli innego uzytkownika.
     *
     * <p>Uzywamy PATCH, a nie PUT - zmieniamy JEDNO pole, a nie podmieniamy
     * calego zasobu (wyklad 4, slajd 5 o metodach HTTP).</p>
     *
     * <p>Login administratora bierzemy z sesji ({@code authentication}),
     * nigdy z tresci zapytania - inaczej dalo by sie obejsc blokade zmiany
     * wlasnej roli, podajac w JSON-ie cudzy login.</p>
     */
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

    /**
     * Zakaz publikowania na okreslony czas albo jego zdjecie.
     *
     * <p>PATCH, bo zmieniamy jedno pole. Pusta liczba godzin w tresci zapytania
     * <b>zdejmuje</b> zakaz - to jedna operacja zamiast dwoch endpointow,
     * bo w panelu jest to jeden przelacznik.</p>
     *
     * <p>Zakaz dotyczy dodawania i edytowania postow. Czytanie, komentowanie
     * reakcja i usuwanie wlasnych tresci zostaja dozwolone - kara ma
     * powstrzymac przed publikowaniem, a nie odciac od portalu. Od odciecia
     * jest usuniecie konta.</p>
     */
    @PatchMapping("/{id}/posting-ban")
    @Operation(summary = "Naklada albo zdejmuje zakaz publikowania (tylko administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zakaz nalozony albo zdjety"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnien administratora"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409", description = "Proba zablokowania samego siebie"),
        @ApiResponse(responseCode = "422", description = "Liczba godzin poza zakresem 1-8760")
    })
    public ResponseEntity<AdminUserResponse> setPostingBan(
            @PathVariable Long id,
            @Valid @RequestBody PostingBanRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            moderationService.setPostingBan(authentication.getName(), id, payload));
    }

    /**
     * Usuwa konto razem z jego postami, reakcjami, znajomosciami i plikami.
     *
     * <p>Odpowiadamy kodem 204 (no content), a nie 200 z trescia - po
     * usunieciu nie ma juz czego zwrocic (wyklad 4, slajd 32).</p>
     */
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

    /**
     * Naklada albo zdejmuje <b>zakaz wysylania wiadomosci</b>.
     *
     * <p>Osobny od zakazu publikowania i to jest cala roznica: ktos moze
     * zasmiecac tablice, nie dokuczajac nikomu prywatnie - i odwrotnie.
     * Jeden przelacznik na oba przypadki nie pozwalalby wyrazic zadnego
     * z nich osobno.</p>
     */
    @PatchMapping("/{id}/messaging-ban")
    @Operation(summary = "Naklada albo zdejmuje zakaz wysylania wiadomosci (tylko administrator)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zakaz nalozony albo zdjety"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnien administratora"),
        @ApiResponse(responseCode = "404", description = "Nie ma takiego uzytkownika"),
        @ApiResponse(responseCode = "409", description = "Proba zablokowania samego siebie"),
        @ApiResponse(responseCode = "422", description = "Liczba godzin poza zakresem 1-8760")
    })
    public ResponseEntity<AdminUserResponse> setMessagingBan(
            @PathVariable Long id,
            @Valid @RequestBody MessagingBanRequest payload,
            Authentication authentication) {

        return ResponseEntity.ok(
            moderationService.setMessagingBan(authentication.getName(), id, payload));
    }

    /**
     * Konta logujace sie z tych samych adresow co wskazane - <b>poszlaka
     * multikonta</b>.
     *
     * <p>Odpowiedz zawiera adres, liczbe logowan i date ostatniego. To nie
     * jest dowod: pod jednym adresem siedzi cala rodzina, akademik albo
     * tysiace klientow operatora komorkowego. Dlatego aplikacja nikogo tu
     * nie blokuje sama - pokazuje dane i zostawia decyzje czlowiekowi.</p>
     */
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

    /**
     * Blokuje adres sieciowy.
     *
     * <p>Blokada dziala przy <b>rejestracji i logowaniu</b>, a nie na calym
     * ruchu - dlaczego, opisuje encja {@code BlockedIp}. Adresu, z ktorego
     * administrator wlasnie korzysta, zablokowac sie nie da: przy testowaniu
     * na jednym komputerze odcialby sam siebie.</p>
     */
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
