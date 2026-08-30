package com.musicclubapp.controller;

import com.musicclubapp.dto.LoginRequest;
import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.security.JsonRememberMeServices;
import com.musicclubapp.service.NetworkService;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Rejestracja, logowanie i sprawdzenie kto jest zalogowany. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Uwierzytelnianie", description = "Rejestracja, logowanie i wylogowanie")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final JsonRememberMeServices rememberMeServices;
    private final NetworkService network;
    private final UserRepository userRepository;

    public AuthController(UserService userService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          JsonRememberMeServices rememberMeServices,
                          NetworkService network,
                          UserRepository userRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.rememberMeServices = rememberMeServices;
        this.network = network;
        this.userRepository = userRepository;
    }

    /** Zakladanie konta. @Valid uruchamia walidacje DTO (wyklad 3, slajd 62). */
    @PostMapping("/register")
    @Operation(summary = "Zaklada nowe konto uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Konto zalozone"),
        @ApiResponse(responseCode = "409", description = "Login lub e-mail juz zajety"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji danych")
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        /* Blokada adresu dziala WLASNIE tutaj - przy zakladaniu konta. */
        network.requireNotBlocked(network.clientIp(http));

        UserResponse created = userService.register(request);

        URI location = UriComponentsBuilder.fromPath("/api/users/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /** Logowanie - reczny przebieg z wykladu 7, slajd 33. */
    @PostMapping("/login")
    @Operation(summary = "Loguje uzytkownika i zaklada sesje")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zalogowano"),
        @ApiResponse(responseCode = "401", description = "Bledny login lub haslo")
    })
    public ResponseEntity<UserResponse> username(@Valid @RequestBody LoginRequest loginPayload,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {

        String address = network.clientIp(request);
        /* Sprawdzamy PRZED sprawdzeniem hasla. */
        network.requireNotBlocked(address);

        Authentication payload = new UsernamePasswordAuthenticationToken(
            loginPayload.username(),
            loginPayload.password());

        Authentication authenticated = authenticationManager.authenticate(payload);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        /* "Zapamietaj mnie" (wymaganie nr 17). */
        if (loginPayload.rememberMe()) {
            rememberMeServices.rememberUser(request, response, authenticated);
        }

        /* Zapisujemy adres dopiero po UDANYM zalogowaniu. */
        userRepository.findByUsername(authenticated.getName())
            .ifPresent(user -> network.recordLogin(user, address));

        return ResponseEntity.ok(userService.getByUsername(authenticated.getName()));
    }

    /** Kto jest aktualnie zalogowany. */
    @GetMapping("/me")
    @Operation(summary = "Zwraca dane zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane uzytkownika"),
        @ApiResponse(responseCode = "401", description = "Nikt nie jest zalogowany")
    })
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userService.getByUsername(authentication.getName()));
    }

    /**
     * Endpoint istnieje wylacznie po to, zeby wymusic ustawienie ciasteczka XSRF-TOKEN, zanim
     * frontend wysle pierwsze zapytanie POST.
     */
    @GetMapping("/csrf")
    @Operation(summary = "Ustawia ciasteczko CSRF przed pierwszym zapytaniem POST")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
