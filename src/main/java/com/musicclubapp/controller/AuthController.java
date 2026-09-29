package com.musicclubapp.controller;

import com.musicclubapp.dto.EmailChangeInfoResponse;
import com.musicclubapp.dto.EmailVerificationResponse;
import com.musicclubapp.dto.LoginRequest;
import com.musicclubapp.dto.NewPasswordRequest;
import com.musicclubapp.dto.PasswordResetInfoResponse;
import com.musicclubapp.dto.PasswordResetRequest;
import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.ResendVerificationRequest;
import com.musicclubapp.dto.TokenRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.dto.VerifyEmailRequest;
import com.musicclubapp.security.SecurityStampFilter;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.security.JsonRememberMeServices;
import com.musicclubapp.service.EmailVerificationService;
import com.musicclubapp.service.MailRateLimiter;
import com.musicclubapp.service.NetworkService;
import com.musicclubapp.service.PasswordResetService;
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
    private final EmailVerificationService emailVerification;
    private final MailRateLimiter mailLimiter;
    private final PasswordResetService passwordReset;

    public AuthController(UserService userService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          JsonRememberMeServices rememberMeServices,
                          NetworkService network,
                          UserRepository userRepository,
                          EmailVerificationService emailVerification,
                          MailRateLimiter mailLimiter,
                          PasswordResetService passwordReset) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.rememberMeServices = rememberMeServices;
        this.network = network;
        this.userRepository = userRepository;
        this.emailVerification = emailVerification;
        this.mailLimiter = mailLimiter;
        this.passwordReset = passwordReset;
    }

    /** Zakladanie konta. @Valid uruchamia walidacje DTO (wyklad 3, slajd 62). */
    @PostMapping("/register")
    @Operation(summary = "Zaklada nowe konto uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Konto zalozone"),
        @ApiResponse(responseCode = "409", description = "Login lub e-mail juz zajety"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji danych"),
        @ApiResponse(responseCode = "429", description = "Za duzo rejestracji z tego adresu sieciowego")
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        /* Blokada adresu dziala WLASNIE tutaj - przy zakladaniu konta. */
        String address = network.clientIp(http);
        network.requireNotBlocked(address);

        /* Kazda rejestracja wysyla wiadomosc - limit, zeby nie dalo sie nami zasypywac cudzych skrzynek. */
        if (emailVerification.enabled()) {
            mailLimiter.acquire(address);
        }

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

        /* Dopiero po hasle - inaczej odpowiedz zdradzalaby, ktore konta nie sa potwierdzone. */
        emailVerification.requireVerified(authenticated.getName());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        /* Znacznik bezpieczenstwa z chwili logowania - po jego zmianie sesja wygasnie. */
        request.getSession().setAttribute(SecurityStampFilter.ATTR,
            userService.securityStampOf(authenticated.getName()));

        /* "Zapamietaj mnie" (wymaganie nr 17). */
        if (loginPayload.rememberMe()) {
            rememberMeServices.rememberUser(request, response, authenticated);
        }

        /* Zapisujemy adres dopiero po UDANYM zalogowaniu. */
        userRepository.findByUsername(authenticated.getName())
            .ifPresent(user -> network.recordLogin(user, address));

        return ResponseEntity.ok(userService.getByUsername(authenticated.getName()));
    }

    /** Klikniety link z wiadomosci. Nie loguje - link mogl zostac otwarty na innym urzadzeniu. */
    @PostMapping("/verify-email")
    @Operation(summary = "Potwierdza adres e-mail tokenem z wiadomosci")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adres potwierdzony"),
        @ApiResponse(responseCode = "409", description = "Link niewazny, zuzyty albo adres zajety")
    })
    public ResponseEntity<EmailVerificationResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(emailVerification.verify(request.token()));
    }

    /**
     * Ponowna wysylka linku przed pierwszym zalogowaniem - z haslem. Opcjonalnie
     * na poprawiony adres, gdy przy rejestracji wkradla sie literowka.
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Wysyla ponownie link potwierdzajacy (login i haslo wymagane)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Wyslano"),
        @ApiResponse(responseCode = "401", description = "Bledny login lub haslo"),
        @ApiResponse(responseCode = "409", description = "Adres juz potwierdzony albo zajety"),
        @ApiResponse(responseCode = "429", description = "Za czesto - odpowiedz mowi, ile poczekac")
    })
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                                   HttpServletRequest http) {
        String address = network.clientIp(http);
        network.requireNotBlocked(address);

        Authentication authenticated = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        mailLimiter.acquire(address);
        emailVerification.resendRegistration(authenticated.getName(), request.email());
        return ResponseEntity.noContent().build();
    }

    /** Strona zgody na zmiane adresu: czyje konto i na jaki (zamaskowany) adres. */
    @PostMapping("/email-change/info")
    @Operation(summary = "Sprawdza link zgody na zmiane adresu e-mail")
    public ResponseEntity<EmailChangeInfoResponse> emailChangeInfo(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(emailVerification.changeInfo(request.token()));
    }

    /** Zgoda ze STAREGO adresu na zmiane. */
    @PostMapping("/email-change/approve")
    @Operation(summary = "Zgoda ze starego adresu na zmiane adresu e-mail")
    public ResponseEntity<EmailVerificationResponse> approveEmailChange(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(emailVerification.approveChange(request.token()));
    }

    /** "To nie ja" - zmiana przepada, wszystkie urzadzenia wylogowane. */
    @PostMapping("/email-change/deny")
    @Operation(summary = "Odrzuca zmiane adresu e-mail i wylogowuje wszystkie urzadzenia")
    public ResponseEntity<EmailVerificationResponse> denyEmailChange(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(emailVerification.denyChange(request.token()));
    }

    /**
     * "Nie pamietam hasla". Zawsze 204 - takze gdy konta z tym adresem nie ma
     * albo limit sie wyczerpal; inaczej dalo sie sprawdzac, kto ma konto.
     * Limit na adres IP jest jawny (429) - nie zdradza niczego o kontach.
     */
    @PostMapping("/password-reset/request")
    @Operation(summary = "Wysyla link do ustawienia nowego hasla (zawsze 204)")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                     HttpServletRequest http) {
        String address = network.clientIp(http);
        network.requireNotBlocked(address);
        mailLimiter.acquire(address);
        passwordReset.request(request.email());
        return ResponseEntity.noContent().build();
    }

    /** Czy link resetu jest wazny - przed pokazaniem formularza. */
    @PostMapping("/password-reset/check")
    @Operation(summary = "Sprawdza link resetu hasla")
    public ResponseEntity<PasswordResetInfoResponse> checkPasswordReset(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(passwordReset.check(request.token()));
    }

    /** Nowe haslo z linku. Wszystkie inne urzadzenia zostaja wylogowane. */
    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Ustawia nowe haslo z linku resetu")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody NewPasswordRequest request) {
        passwordReset.confirm(request.token(), request.password());
        return ResponseEntity.noContent().build();
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
