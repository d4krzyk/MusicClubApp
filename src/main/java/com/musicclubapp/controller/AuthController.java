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

/**
 * Rejestracja, logowanie i sprawdzenie kto jest zalogowany.
 *
 * <p><b>Konwencje z wykladow:</b></p>
 * <ul>
 *   <li>kontroler nie dotyka repozytorium, tylko serwisu (wyklad 4, slajd 13),</li>
 *   <li>kazda metoda zwraca {@link ResponseEntity} - wymaganie nr 22
 *       (wyklad 4, slajd 27),</li>
 *   <li>statusy HTTP wedlug wykladu 4, slajd 32: 201 przy utworzeniu zasobu,
 *       204 gdy nie ma czego zwracac,</li>
 *   <li>reczny przebieg logowania - wyklad 7, slajd 33.</li>
 * </ul>
 *
 * <p>Adnotacje {@code @Operation} i {@code @ApiResponses} opisuja endpointy
 * w Swaggerze (wymaganie nr 24) - dokumentacja pod
 * {@code http://localhost:8080/swagger-ui.html}.</p>
 */
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

    /**
     * Zakladanie konta.
     *
     * <p>{@code @Valid} uruchamia walidacje DTO (wyklad 3, slajd 62). Jesli
     * cokolwiek jest nie tak, metoda w ogole sie nie wykona - wyjatek zlapie
     * {@code GlobalExceptionHandler} i odesle 422 z lista blednych pol.</p>
     *
     * <p>Zwracamy 201 CREATED razem z naglowkiem {@code Location} wskazujacym
     * utworzony zasob - wyklad 4 (slajd 32) mowi, ze odpowiedz na POST
     * "powinna zawierac link/sciezke do utworzonego zasobu".</p>
     */
    @PostMapping("/register")
    @Operation(summary = "Zaklada nowe konto uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Konto zalozone"),
        @ApiResponse(responseCode = "409", description = "Login lub e-mail juz zajety"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji danych")
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        /*
         * Blokada adresu dziala WLASNIE tutaj - przy zakladaniu konta.
         * To jest jedyny moment, w ktorym da sie zatrzymac osobe wracajaca
         * po banie pod nowym loginem; po zalozeniu konta jest juz tylko
         * kolejnym uzytkownikiem, nie do odroznienia od reszty.
         */
        network.requireNotBlocked(network.clientIp(http));

        UserResponse created = userService.register(request);

        URI location = UriComponentsBuilder.fromPath("/api/users/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * Logowanie - reczny przebieg z wykladu 7, slajd 33.
     *
     * <p>Krok po kroku:</p>
     * <ol>
     *   <li>pakujemy login i haslo w {@link UsernamePasswordAuthenticationToken}
     *       (to jeszcze NIE jest dowod tozsamosci, tylko "prosba o sprawdzenie"),</li>
     *   <li>{@code authenticationManager.authenticate(...)} sprawdza haslo -
     *       przy blednym rzuca {@code BadCredentialsException}, ktory zamieniamy
     *       na 401 w {@code GlobalExceptionHandler},</li>
     *   <li>wynik wkladamy do {@code SecurityContext} i zapisujemy w sesji.</li>
     * </ol>
     *
     * <p><b>Uzupelnienie wzgledem slajdu 33:</b> tam jest tylko
     * {@code SecurityContextHolder.getContext().setAuthentication(...)}.
     * To ustawia uzytkownika na czas biezacego zapytania, ale od Spring
     * Security 6 <b>nie zapisuje go w sesji</b> - przy nastepnym zapytaniu
     * uzytkownik znowu bylby niezalogowany. Dlatego dochodzi jawne
     * {@code securityContextRepository.saveContext(...)}.</p>
     */
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
        /*
         * Sprawdzamy PRZED sprawdzeniem hasla. Odwrotna kolejnosc oznaczalaby,
         * ze zablokowany adres nadal moze sprawdzac hasla - czyli ze blokada
         * nie przeszkadza w zgadywaniu ich metoda prob i bledow.
         */
        network.requireNotBlocked(address);

        Authentication payload = new UsernamePasswordAuthenticationToken(
            loginPayload.username(),
            loginPayload.password());

        Authentication authenticated = authenticationManager.authenticate(payload);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        /*
         * "Zapamietaj mnie" (wymaganie nr 17). Przy formularzu logowania Springa
         * dzieje sie to samo z siebie, ale my logujemy recznie, wiec sami
         * prosimy o wystawienie dlugotrwalego ciasteczka.
         */
        if (loginPayload.rememberMe()) {
            rememberMeServices.rememberUser(request, response, authenticated);
        }

        /*
         * Zapisujemy adres dopiero po UDANYM zalogowaniu. Zapis przy kazdej
         * probie zamienialby te tabele w dziennik nieudanych logowan - a to
         * zupelnie inna funkcja, ktorej tu nie ma.
         *
         * Blad zapisu nie moze przerwac logowania: to notatka pomocnicza dla
         * administratora, a nie warunek wejscia (patrz NetworkService).
         */
        userRepository.findByUsername(authenticated.getName())
            .ifPresent(user -> network.recordLogin(user, address));

        return ResponseEntity.ok(userService.getByUsername(authenticated.getName()));
    }

    /**
     * Kto jest aktualnie zalogowany.
     *
     * <p>Frontend wola to przy starcie, zeby wiedziec, czy pokazac ekran
     * logowania czy strone glowna. Przy okazji ustawia sie ciasteczko
     * CSRF, potrzebne do pozniejszych zapytan POST.</p>
     *
     * <p>{@code authentication.getName()} zwraca login zalogowanego -
     * wyklad 7, slajd 30.</p>
     */
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
     * Endpoint istnieje wylacznie po to, zeby wymusic ustawienie ciasteczka
     * XSRF-TOKEN, zanim frontend wysle pierwsze zapytanie POST.
     *
     * <p><b>Dlaczego {@link CsrfToken} jest w argumentach, skoro go nie
     * zwracamy?</b> Spring Security 6 generuje token CSRF <i>leniwie</i> -
     * dopiero wtedy, gdy cos faktycznie o niego poprosi. Sam pusty endpoint
     * nie wystarczy: odpowiedz przychodzi bez naglowka {@code Set-Cookie},
     * frontend nie ma czym podpisac zapytania POST i dostaje odmowe.
     * Samo wywolanie {@code getToken()} nizej wymusza wygenerowanie tokenu
     * i zapisanie go w ciasteczku.</p>
     *
     * <p>204 NO CONTENT - "sukces, ale nie ma czego zwracac"
     * (wyklad 4, slajd 32).</p>
     */
    @GetMapping("/csrf")
    @Operation(summary = "Ustawia ciasteczko CSRF przed pierwszym zapytaniem POST")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
