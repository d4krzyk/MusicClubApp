package com.musicclubapp.controller;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.ChangeRoleRequest;
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
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public UserController(UserService userService) {
        this.userService = userService;
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
            @Valid @RequestBody ChangeRoleRequest zadanie,
            Authentication authentication) {

        return ResponseEntity.ok(
            userService.changeRole(authentication.getName(), id, zadanie));
    }
}
