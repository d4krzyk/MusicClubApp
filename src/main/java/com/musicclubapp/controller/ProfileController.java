package com.musicclubapp.controller;

import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.ConfirmPasswordRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.ExportRequest;
import com.musicclubapp.dto.PrivacySettings;
import com.musicclubapp.dto.TermsStatusResponse;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.security.JsonRememberMeServices;
import com.musicclubapp.security.SecurityStampFilter;
import com.musicclubapp.service.AccountDeletionService;
import com.musicclubapp.service.EmailVerificationService;
import com.musicclubapp.service.DataExportService;
import com.musicclubapp.service.PrivacyService;
import com.musicclubapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Wlasny profil zalogowanego uzytkownika - ustawienia konta. */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profil", description = "Ustawienia wlasnego konta")
public class ProfileController {

    private final UserService userService;
    private final AccountDeletionService deletion;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;
    private final EmailVerificationService emailVerification;
    private final JsonRememberMeServices rememberMeServices;
    private final PrivacyService privacy;
    private final DataExportService dataExport;

    public ProfileController(UserService userService,
                             AccountDeletionService deletion,
                             UserDetailsService userDetailsService,
                             SecurityContextRepository securityContextRepository,
                             EmailVerificationService emailVerification,
                             JsonRememberMeServices rememberMeServices,
                             PrivacyService privacy,
                             DataExportService dataExport) {
        this.dataExport = dataExport;
        this.privacy = privacy;
        this.userService = userService;
        this.deletion = deletion;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.emailVerification = emailVerification;
        this.rememberMeServices = rememberMeServices;
    }

    /** Zmiana loginu i adresu e-mail. */
    @PutMapping
    @Operation(summary = "Zmienia login i adres e-mail zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zapisano"),
        @ApiResponse(responseCode = "409", description = "Login lub e-mail zajety przez kogos innego"),
        @ApiResponse(responseCode = "422", description = "Blad walidacji")
    })
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest payload,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        UserResponse updated = userService.updateProfile(authentication.getName(), payload);

        if (!authentication.getName().equals(updated.username())) {
            refreshSession(updated.username(), request, response);
        }

        return ResponseEntity.ok(updated);
    }

    /** Link na nowy adres jeszcze raz - gdy pierwsza wiadomosc nie doszla. */
    @PostMapping("/email/resend")
    @Operation(summary = "Wysyla ponownie link potwierdzajacy nowy adres e-mail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wyslano"),
        @ApiResponse(responseCode = "409", description = "Nie ma zmiany adresu do potwierdzenia"),
        @ApiResponse(responseCode = "429", description = "Za czesto")
    })
    public ResponseEntity<UserResponse> resendEmailChange(Authentication authentication) {
        emailVerification.resendChange(authentication.getName());
        return ResponseEntity.ok(userService.getByUsername(authentication.getName()));
    }

    /** Rezygnacja ze zmiany adresu - zostaje dotychczasowy. */
    @DeleteMapping("/email/pending")
    @Operation(summary = "Anuluje zmiane adresu e-mail czekajaca na potwierdzenie")
    public ResponseEntity<UserResponse> cancelEmailChange(Authentication authentication) {
        emailVerification.cancelChange(authentication.getName());
        return ResponseEntity.ok(userService.getByUsername(authentication.getName()));
    }

    /**
     * Pobranie wlasnych danych (ZIP). POST, a nie GET: wymaga hasla w tresci, a do tego zmienia
     * stan (limit jednego pobrania na minute) - i chroni go CSRF. Dane zbieramy w transakcji, a do
     * strumienia zapisujemy juz po niej.
     */
    @PostMapping("/export")
    @Operation(summary = "Pobiera archiwum ZIP z wlasnymi danymi (wymaga hasla)")
    public ResponseEntity<StreamingResponseBody> export(@RequestBody ExportRequest payload,
                                                        Authentication authentication) {
        DataExportService.Eksport eksport = dataExport.przygotuj(authentication.getName(), payload.currentPassword());
        String nazwa = "musicclub-dane-" + eksport.login() + "-" + java.time.LocalDate.now() + ".zip";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nazwa).build().toString())
            // Zawartosc jest jednorazowa i wrazliwa - zadnych kopii w przegladarce ani posrednikach
            .cacheControl(CacheControl.noStore())
            .body(out -> dataExport.zapisz(eksport, out));
    }

    @GetMapping("/terms")
    @Operation(summary = "Ktora wersje regulaminu ma zaakceptowana to konto")
    public ResponseEntity<TermsStatusResponse> terms(Authentication authentication) {
        return ResponseEntity.ok(userService.termsStatus(authentication.getName()));
    }

    @PostMapping("/terms/accept")
    @Operation(summary = "Akceptuje obecna wersje regulaminu i polityki prywatnosci")
    public ResponseEntity<TermsStatusResponse> acceptTerms(Authentication authentication) {
        return ResponseEntity.ok(userService.acceptTerms(authentication.getName()));
    }

    @GetMapping("/privacy")
    @Operation(summary = "Moje ustawienia prywatnosci")
    public ResponseEntity<PrivacySettings> privacy(Authentication authentication) {
        return ResponseEntity.ok(privacy.settings(authentication.getName()));
    }

    @PutMapping("/privacy")
    @Operation(summary = "Zapisuje ustawienia prywatnosci")
    public ResponseEntity<PrivacySettings> updatePrivacy(@Valid @RequestBody PrivacySettings payload,
                                                         Authentication authentication) {
        return ResponseEntity.ok(privacy.update(authentication.getName(), payload));
    }

    /** Zmiana hasla. */
    @PutMapping("/password")
    @Operation(summary = "Zmienia haslo zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Haslo zmienione"),
        @ApiResponse(responseCode = "422", description = "Bledne obecne haslo albo blad walidacji")
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest payload,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        userService.changePassword(authentication.getName(), payload);
        zostanZalogowanyTutaj(authentication, request, response);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Wylogowuje wszystkie inne urzadzenia - to zostaje zalogowane. */
    @PostMapping("/sessions/revoke-others")
    @Operation(summary = "Wylogowuje wszystkie inne urzadzenia")
    public ResponseEntity<Void> revokeOtherSessions(Authentication authentication,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        userService.revokeOtherSessions(authentication.getName());
        zostanZalogowanyTutaj(authentication, request, response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Po zmianie znacznika bezpieczenstwa: biezaca sesja dostaje nowy (inne
     * odpadna), a jesli to urzadzenie mialo "zapamietaj mnie" - nowe ciasteczko,
     * bo stare przestalo pasowac do podpisu.
     */
    private void zostanZalogowanyTutaj(Authentication authentication, HttpServletRequest request,
                                       HttpServletResponse response) {
        request.getSession().setAttribute(SecurityStampFilter.ATTR,
            userService.securityStampOf(authentication.getName()));
        boolean zapamietane = request.getCookies() != null
            && java.util.Arrays.stream(request.getCookies()).anyMatch(c -> "remember-me".equals(c.getName()));
        if (zapamietane) {
            rememberMeServices.rememberUser(request, response, authentication);
        }
    }

    /** Wgranie zdjecia profilowego. */
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ustawia zdjecie profilowe zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zdjecie zapisane"),
        @ApiResponse(responseCode = "422", description = "Plik pusty albo nie jest obrazkiem")
    })
    public ResponseEntity<UserResponse> uploadAvatar(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {

        return ResponseEntity.ok(userService.updateAvatar(authentication.getName(), file));
    }

    /** Usuniecie zdjecia profilowego - wracamy do kola z inicjalem. */
    @DeleteMapping("/avatar")
    @Operation(summary = "Usuwa zdjecie profilowe")
    public ResponseEntity<UserResponse> deleteAvatar(Authentication authentication) {
        return ResponseEntity.ok(userService.removeAvatar(authentication.getName()));
    }

    /**
     * Kasuje wszystkie wlasne posty. Konto zostaje.
     *
     * <p>Haslo w tresci zapytania, a nie samo klikniecie: sesja moze byc
     * otwarta na cudzym komputerze, a tego nie da sie cofnac.</p>
     */
    @DeleteMapping("/posts")
    @Operation(summary = "Kasuje wszystkie posty zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Skasowane; w tresci liczba postow"),
        @ApiResponse(responseCode = "422", description = "Bledne haslo")
    })
    public ResponseEntity<Map<String, Integer>> deleteOwnPosts(
            @Valid @RequestBody ConfirmPasswordRequest payload,
            Authentication authentication) {

        int usuniete = deletion.deleteOwnPosts(
            authentication.getName(), payload.currentPassword());

        return ResponseEntity.ok(Map.of("deleted", usuniete));
    }

    /**
     * Kasuje wlasne konto razem ze wszystkim, co po nim zostalo.
     *
     * <p>Po skasowaniu uniewazniamy sesje - inaczej przegladarka zostaje
     * z ciasteczkiem wskazujacym na konto, ktorego juz nie ma, i kazde
     * nastepne klikniecie konczy sie bledem zamiast ekranem logowania.</p>
     */
    @DeleteMapping
    @Operation(summary = "Kasuje wlasne konto zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Konto usuniete"),
        @ApiResponse(responseCode = "422", description = "Bledne haslo")
    })
    public ResponseEntity<Void> deleteOwnAccount(
            @Valid @RequestBody ConfirmPasswordRequest payload,
            Authentication authentication,
            HttpServletRequest request) {

        deletion.deleteOwnAccount(authentication.getName(), payload.currentPassword());

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Podmienia uzytkownika zapisanego w sesji na tego z nowym loginem, zeby zalogowanie
     * przetrwalo zmiane nazwy konta.
     */
    private void refreshSession(String newUsername,
                              HttpServletRequest request,
                              HttpServletResponse response) {

        UserDetails refreshed = userDetailsService.loadUserByUsername(newUsername);

        Authentication created = new UsernamePasswordAuthenticationToken(
            refreshed, null, refreshed.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(created);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
