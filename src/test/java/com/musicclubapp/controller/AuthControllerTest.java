package com.musicclubapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.config.I18nConfig;
import com.musicclubapp.config.SecurityConfig;
import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.GlobalExceptionHandler;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testy kontrolera - wymaganie nr 25 ({@code @WebMvcTest}).
 *
 * <p>Wyklad 5 (slajdy 21-22): {@code @WebMvcTest} podnosi tylko warstwe MVC.
 * Nie startuje serwer ani baza, nie ma prawdziwych zapytan HTTP - zamiast tego
 * wstrzykujemy {@link MockMvc} i przez niego "udajemy" zapytania. Serwis
 * zastepujemy atrapa ({@code @MockBean}, slajd 29).</p>
 *
 * <p>{@code @Import(SecurityConfig.class)} dociaga nasza konfiguracje
 * bezpieczenstwa - bez tego test chodzilby na domyslnej konfiguracji Springa
 * i nie sprawdzalby tego, co naprawde mamy w projekcie.</p>
 *
 * <p>{@code .with(csrf())} dokleja poprawny token CSRF. Bez niego kazdy POST
 * dostalby 403 - i to jest dowod, ze ochrona CSRF faktycznie dziala.</p>
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, I18nConfig.class, GlobalExceptionHandler.class})
@DisplayName("AuthController - rejestracja i logowanie przez HTTP")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationManager authenticationManager;

    /**
     * Potrzebny konfiguracji Security do zbudowania obslugi "zapamietaj mnie".
     *
     * <p>Uwaga na pulapke: {@code SecurityContextRepository} celowo NIE jest
     * tu atrapa. Gdyby byl, jego {@code loadDeferredContext()} zwracalby
     * {@code null}, a filtr Springa wymaga niepustej wartosci - wszystkie
     * testy leca wtedy na "Only non-null Supplier instances are permitted".
     * Prawdziwy bean pochodzi z zaimportowanego {@link SecurityConfig}.</p>
     */
    @MockBean
    private UserDetailsService userDetailsService;

    /** Potrzebne walidatorowi @UniqueUsername, ktory zaglada do bazy. */
    @MockBean
    private UserRepository userRepository;

    private String json(Object obiekt) throws Exception {
        return objectMapper.writeValueAsString(obiekt);
    }

    @Test
    @DisplayName("poprawna rejestracja zwraca 201 i naglowek Location")
    void poprawnaRejestracjaZwraca201() throws Exception {
        given(userRepository.existsByUsername("anna")).willReturn(false);
        given(userService.register(any(RegisterRequest.class))).willReturn(
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now()));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest(
                    "anna", "anna@example.com", "tajneHaslo1", "tajneHaslo1"))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.username").value("anna"))
            // hash hasla NIE moze wyciec w odpowiedzi
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("bledny e-mail konczy sie kodem 422 i wskazaniem pola")
    void blednyEmailZwraca422() throws Exception {
        given(userRepository.existsByUsername("anna")).willReturn(false);

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest(
                    "anna", "to-nie-jest-email", "tajneHaslo1", "tajneHaslo1"))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    @DisplayName("rozne hasla konczy sie kodem 422 - wlasna adnotacja @PasswordsMatch")
    void rozneHaslaZwracaja422() throws Exception {
        given(userRepository.existsByUsername("anna")).willReturn(false);

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest(
                    "anna", "anna@example.com", "tajneHaslo1", "INNEhaslo9"))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("confirmPassword"));
    }

    @Test
    @DisplayName("za krotkie haslo konczy sie kodem 422")
    void zaKrotkieHasloZwraca422() throws Exception {
        given(userRepository.existsByUsername("anna")).willReturn(false);

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest("anna", "anna@example.com", "krotkie", "krotkie"))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("zajety login konczy sie kodem 409 CONFLICT")
    void zajetyLoginZwraca409() throws Exception {
        given(userRepository.existsByUsername("anna")).willReturn(false);
        given(userService.register(any(RegisterRequest.class)))
            .willThrow(DuplicateResourceException.username("anna"));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest(
                    "anna", "anna@example.com", "tajneHaslo1", "tajneHaslo1"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST bez tokenu CSRF jest odrzucany - dowod, ze ochrona dziala")
    void brakTokenuCsrfZwraca403() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest(
                    "anna", "anna@example.com", "tajneHaslo1", "tajneHaslo1"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/me bez zalogowania zwraca 401, a nie przekierowanie")
    void meBezZalogowaniaZwraca401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("/me zwraca dane zalogowanego, ale NIE ujawnia jego roli")
    void meDlaZalogowanegoZwracaDane() throws Exception {
        given(userService.getByUsername("anna")).willReturn(
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now()));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("anna"))
            // Zwykly uzytkownik nie ma ogladac napisu "USER" - w odpowiedzi
            // jest tylko flaga admin, a samo pole "role" nie istnieje
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("/me dla administratora ustawia flage admin na true")
    void meDlaAdminaUstawiaFlage() throws Exception {
        given(userService.getByUsername("admin")).willReturn(
            new UserResponse(1L, "admin", "admin@musicclub.local", true, null, LocalDateTime.now()));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admin").value(true));
    }
}
