package com.musicclubapp.controller;

import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wlasny profil zalogowanego uzytkownika - ustawienia konta.
 *
 * <p><b>Czym to sie rozni od {@code UserController}?</b> Tamten sluzy do
 * przegladania CUDZYCH kont i jest dostepny tylko dla administratora. Tutaj
 * kazdy zalogowany moze zmienic wylacznie SWOJE dane - nigdzie nie przyjmujemy
 * identyfikatora uzytkownika z zapytania, tylko bierzemy go z sesji
 * ({@code authentication.getName()}). Dzieki temu nie da sie podmienic id
 * w adresie i wejsc w cudze ustawienia.</p>
 */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profil", description = "Ustawienia wlasnego konta")
public class ProfileController {

    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;

    public ProfileController(UserService userService,
                             UserDetailsService userDetailsService,
                             SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Zmiana loginu i adresu e-mail.
     *
     * <p><b>Uwaga na pulapke:</b> sesja zapamietuje uzytkownika po LOGINIE.
     * Po jego zmianie zapisany w sesji login przestaje istniec w bazie
     * i kazde kolejne zapytanie (np. {@code /api/auth/me}) konczyloby sie
     * bledem 404 albo wylogowaniem. Dlatego po udanej zmianie budujemy nowy
     * obiekt uwierzytelnienia i nadpisujemy nim sesje - patrz
     * {@link #odswiezSesje}.</p>
     */
    @PutMapping
    @Operation(summary = "Zmienia login i adres e-mail zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zapisano"),
        @ApiResponse(responseCode = "409", description = "Login lub e-mail zajety przez kogos innego"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji")
    })
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest zadanie,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        UserResponse zaktualizowany = userService.updateProfile(authentication.getName(), zadanie);

        if (!authentication.getName().equals(zaktualizowany.username())) {
            odswiezSesje(zaktualizowany.username(), request, response);
        }

        return ResponseEntity.ok(zaktualizowany);
    }

    /**
     * Zmiana hasla. Wymaga podania obecnego hasla - patrz komentarz
     * w {@link ChangePasswordRequest}.
     *
     * <p>Zwracamy 204 NO CONTENT: operacja sie udala, ale nie ma czego
     * odsylac (wyklad 4, slajd 32).</p>
     */
    @PutMapping("/password")
    @Operation(summary = "Zmienia haslo zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Haslo zmienione"),
        @ApiResponse(responseCode = "422", description = "Bledne obecne haslo albo blad walidacji")
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest zadanie,
            Authentication authentication) {

        userService.changePassword(authentication.getName(), zadanie);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Podmienia uzytkownika zapisanego w sesji na tego z nowym loginem,
     * zeby zalogowanie przetrwalo zmiane nazwy konta.
     */
    private void odswiezSesje(String nowyLogin,
                              HttpServletRequest request,
                              HttpServletResponse response) {

        UserDetails odswiezony = userDetailsService.loadUserByUsername(nowyLogin);

        Authentication nowe = new UsernamePasswordAuthenticationToken(
            odswiezony, null, odswiezony.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(nowe);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
