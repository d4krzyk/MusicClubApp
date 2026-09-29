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

/** Testy kontrolera - wymaganie nr 25 (@WebMvcTest). */
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
    private com.musicclubapp.service.NetworkService network;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationManager authenticationManager;

    /** Potrzebny konfiguracji Security do zbudowania obslugi "zapamietaj mnie". */
    @MockBean
    private UserDetailsService userDetailsService;

    /** Potrzebne walidatorowi @UniqueUsername, ktory zaglada do bazy. */
    @MockBean
    private UserRepository userRepository;

    /** Potwierdzanie adresu - atrapa zachowuje sie jak serwer bez poczty (wylaczone). */
    @MockBean
    private com.musicclubapp.service.EmailVerificationService emailVerification;

    @MockBean
    private com.musicclubapp.service.MailRateLimiter mailLimiter;

    private String json(Object obiekt) throws Exception {
        return objectMapper.writeValueAsString(obiekt);
    }

    @Test
    @DisplayName("poprawna rejestracja zwraca 201 i naglowek Location")
    void validRegistrationReturns201() throws Exception {
        given(userRepository.existsByUsername("anna")).willReturn(false);
        given(userService.register(any(RegisterRequest.class))).willReturn(
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now(), true, null));

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
    void invalidEmailReturns422() throws Exception {
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
    void mismatchedPasswordsReturn422() throws Exception {
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
    void tooShortPasswordReturns422() throws Exception {
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
    void takenUsernameReturns409() throws Exception {
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
    void missingCsrfTokenReturns403() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest(
                    "anna", "anna@example.com", "tajneHaslo1", "tajneHaslo1"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/me bez zalogowania zwraca 401, a nie przekierowanie")
    void meWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna")
    @DisplayName("/me zwraca dane zalogowanego, ale NIE ujawnia jego roli")
    void meReturnsDataForLoggedInUser() throws Exception {
        given(userService.getByUsername("anna")).willReturn(
            new UserResponse(1L, "anna", "anna@example.com", false, null, LocalDateTime.now(), true, null));

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
    void meSetsAdminFlag() throws Exception {
        given(userService.getByUsername("admin")).willReturn(
            new UserResponse(1L, "admin", "admin@musicclub.local", true, null, LocalDateTime.now(), true, null));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admin").value(true));
    }
}
