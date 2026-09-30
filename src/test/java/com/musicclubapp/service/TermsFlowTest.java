package com.musicclubapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regulamin i polityka prywatnosci: zgoda przy rejestracji, wersja na koncie, dane administratora. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
    "app.legal.version=2026-10-01",
    "app.legal.controller=Testowa Firma sp. z o.o.",
    "app.legal.contact-email=kontakt@example.com",
    "app.legal.hosting=Hosting Testowy",
    "app.legal.mail-provider=Poczta Testowa"
})
@DisplayName("Regulamin i polityka prywatnosci")
class TermsFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private EntityManager em;

    private ResultActions zarejestruj(String login, Boolean zgoda) throws Exception {
        Map<String, Object> dane = new HashMap<>();
        dane.put("username", login);
        dane.put("email", login + "@example.com");
        dane.put("password", "haslo-terms-1");
        dane.put("confirmPassword", "haslo-terms-1");
        if (zgoda != null) {
            dane.put("acceptTerms", zgoda);
        }
        return mvc.perform(post("/api/auth/register").with(csrf()).header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dane)));
    }

    @Test
    @DisplayName("bez zgody na regulamin konta sie nie zaklada - ani gdy pola brak, ani gdy jest false")
    void registrationRequiresAcceptance() throws Exception {
        zarejestruj("tm_bez", null).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("acceptTerms"))
            .andExpect(jsonPath("$.errors[0].message").value("Zaakceptuj regulamin i potwierdź zapoznanie się z polityką prywatności"));
        zarejestruj("tm_nie", false).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("acceptTerms"));
        assertThat(users.findByUsername("tm_bez")).isEmpty();
        assertThat(users.findByUsername("tm_nie")).isEmpty();
    }

    @Test
    @DisplayName("ze zgoda: konto zapisuje wersje dokumentow i moment zgody")
    void registrationStoresVersion() throws Exception {
        zarejestruj("tm_ok", true).andExpect(status().isCreated());
        em.flush();
        User u = users.findByUsername("tm_ok").orElseThrow();
        assertThat(u.getTermsVersion()).isEqualTo("2026-10-01");
        assertThat(u.getTermsAcceptedAt()).isNotNull();

        mvc.perform(get("/api/profile/terms").with(user("tm_ok")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("2026-10-01"))
            .andExpect(jsonPath("$.acceptedVersion").value("2026-10-01"))
            .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    @DisplayName("konto sprzed regulaminu albo ze starsza wersja dostaje prosbe o akceptacje i moze ja zlozyc")
    void oldAccountsAcceptLater() throws Exception {
        users.save(new User("tm_stare", "tm_stare@example.com", "x"));
        User stare = users.save(new User("tm_starsze", "tm_starsze@example.com", "x"));
        stare.acceptTerms("2020-01-01", LocalDateTime.of(2020, 1, 1, 12, 0));
        em.flush();

        mvc.perform(get("/api/profile/terms").with(user("tm_stare")))
            .andExpect(jsonPath("$.current").value(false)).andExpect(jsonPath("$.acceptedVersion").doesNotExist());
        mvc.perform(get("/api/profile/terms").with(user("tm_starsze")))
            .andExpect(jsonPath("$.current").value(false)).andExpect(jsonPath("$.acceptedVersion").value("2020-01-01"));

        mvc.perform(post("/api/profile/terms/accept").with(user("tm_starsze")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.current").value(true))
            .andExpect(jsonPath("$.acceptedVersion").value("2026-10-01"));
        // Akceptacja jednego konta nie rusza innych
        mvc.perform(get("/api/profile/terms").with(user("tm_stare"))).andExpect(jsonPath("$.current").value(false));
        // Bez logowania i bez tokenu - nic z tego nie dziala
        mvc.perform(get("/api/profile/terms")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/profile/terms/accept").with(user("tm_stare"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dane administratora i wersja idą z konfiguracji serwera, dostepne bez logowania")
    void publicInfoCarriesLegalData() throws Exception {
        mvc.perform(get("/api/public/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.termsVersion").value("2026-10-01"))
            .andExpect(jsonPath("$.controller").value("Testowa Firma sp. z o.o."))
            .andExpect(jsonPath("$.contactEmail").value("kontakt@example.com"))
            .andExpect(jsonPath("$.hosting").value("Hosting Testowy"))
            .andExpect(jsonPath("$.mailProvider").value("Poczta Testowa"));
    }
}
