package com.musicclubapp.controller;

import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.ConfirmPasswordRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.service.AccountDeletionService;
import com.musicclubapp.service.EmailVerificationService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    public ProfileController(UserService userService,
                             AccountDeletionService deletion,
                             UserDetailsService userDetailsService,
                             SecurityContextRepository securityContextRepository,
                             EmailVerificationService emailVerification) {
        this.userService = userService;
        this.deletion = deletion;
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
        this.emailVerification = emailVerification;
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

    /** Zmiana hasla. */
    @PutMapping("/password")
    @Operation(summary = "Zmienia haslo zalogowanego uzytkownika")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Haslo zmienione"),
        @ApiResponse(responseCode = "422", description = "Bledne obecne haslo albo blad walidacji")
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest payload,
            Authentication authentication) {

        userService.changePassword(authentication.getName(), payload);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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
