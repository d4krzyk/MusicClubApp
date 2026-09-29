package com.musicclubapp.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reset hasla i wylogowanie innych urzadzen - przez prawdziwe API i Spring
 * Security, z udawanym serwerem SMTP.
 */
@SpringBootTest(properties = "app.mail.per-ip-per-hour=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PocztaTestowa.Poczta.class)
@DisplayName("Reset hasla i wylogowanie innych urzadzen")
class PasswordResetFlowTest extends PocztaTestowa {

    private static final String NOWE = "zupelnie-nowe-9";

    private ResultActions poprosOReset(String email) throws Exception {
        return postJson("/api/auth/password-reset/request", "{\"email\":\"" + email + "\"}");
    }

    private ResultActions ustawHaslo(String token, String haslo, String powtorzone) throws Exception {
        return postJson("/api/auth/password-reset/confirm",
            "{\"token\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(token, haslo, powtorzone));
    }

    /** Logowanie z "zapamietaj mnie" - zwraca samo ciasteczko. */
    private Cookie zapamietaj(String login) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\",\"rememberMe\":true}".formatted(login, HASLO)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getCookie("remember-me");
    }

    @Test
    @DisplayName("reset: link na adres konta, nowe haslo dziala, stare nie - i wszystko inne jest wylogowane")
    void resetLogsOutEverywhere() throws Exception {
        MockHttpSession innaSesja = potwierdzone("pr_ala", "ala.reset@example.com");
        Cookie ciasteczko = zapamietaj("pr_ala");
        assertThat(ciasteczko).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(ciasteczko)).andExpect(status().isOk());

        // Wielkosc liter nie ma znaczenia
        poprosOReset("ALA.Reset@Example.com").andExpect(status().isNoContent());
        Wiadomosc w = ostatniaDo("ala.reset@example.com");
        assertThat(w.temat()).isEqualTo("Ustaw nowe hasło w MusicClub");
        assertThat(w.html()).contains("/nowe-haslo?token=", "Link działa przez godzinę i tylko raz.");

        postJson("/api/auth/password-reset/check", "{\"token\":\"" + w.token() + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("pr_ala"));

        ustawHaslo(w.token(), NOWE, "inne-haslo-1").andExpect(status().isUnprocessableEntity());
        ustawHaslo(w.token(), NOWE, NOWE).andExpect(status().isNoContent());

        zaloguj("pr_ala", HASLO).andExpect(status().isUnauthorized());
        zaloguj("pr_ala", NOWE).andExpect(status().isOk());

        // Sesja otwarta przed resetem i ciasteczko "zapamietaj mnie" juz nie wpuszczaja
        mvc.perform(get("/api/auth/me").session(innaSesja)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(ciasteczko)).andExpect(status().isUnauthorized());

        // Powiadomienie o zmianie hasla - z przyciskiem na wypadek, gdyby to nie wlasciciel
        Wiadomosc powiadomienie = ostatniaDo("ala.reset@example.com");
        assertThat(powiadomienie.temat()).isEqualTo("Hasło do MusicClub zostało zmienione");
        assertThat(powiadomienie.html()).contains("/reset-hasla").doesNotContain("{{", "Link działa");

        // Link jest jednorazowy
        ustawHaslo(w.token(), "jeszcze-inne-1", "jeszcze-inne-1").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("prosba o reset niczego nie zdradza: nieznany adres i wyczerpany limit tez daja 204")
    void resetDoesNotRevealAccounts() throws Exception {
        potwierdzone("pr_bartek", "bartek.reset@example.com");
        long przed = skrzynka.wyslane.size();

        poprosOReset("nikt-taki@example.com").andExpect(status().isNoContent());
        assertThat(skrzynka.wyslane).hasSize((int) przed);

        poprosOReset("bartek.reset@example.com").andExpect(status().isNoContent());
        poprosOReset("bartek.reset@example.com").andExpect(status().isNoContent());
        // Druga prosba w ciagu minuty - bez wiadomosci, ale tez bez 429
        assertThat(skrzynka.wyslane).hasSize((int) przed + 1);
    }

    @Test
    @DisplayName("reset idzie na obecny adres, nie na ten czekajacy na zmiane")
    void resetGoesToCurrentAddressOnly() throws Exception {
        MockHttpSession sesja = potwierdzone("pr_czesia", "czesia@example.com");
        cofnijWysylki("pr_czesia", 120);
        mvc.perform(put("/api/profile").with(csrf()).session(sesja)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"pr_czesia\",\"email\":\"czesia.nowa@example.com\",\"currentPassword\":\""
                    + HASLO + "\"}"))
            .andExpect(status().isOk());
        long naNowy = ileDo("czesia.nowa@example.com");

        poprosOReset("czesia.nowa@example.com").andExpect(status().isNoContent());
        assertThat(ileDo("czesia.nowa@example.com")).isEqualTo(naNowy);
    }

    @Test
    @DisplayName("\"wyloguj z innych urzadzen\": inne sesje i ciasteczka odpadaja, biezaca zostaje")
    void revokeOtherSessions() throws Exception {
        MockHttpSession tutaj = potwierdzone("pr_darek", "darek@example.com");
        MockHttpSession gdzieIndziej = zalogowany("pr_darek");
        Cookie ciasteczko = zapamietaj("pr_darek");

        mvc.perform(post("/api/profile/sessions/revoke-others").with(csrf()).session(tutaj))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(tutaj)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(gdzieIndziej)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(ciasteczko)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("zmiana hasla w ustawieniach: inne urzadzenia wylogowane, to zostaje, idzie powiadomienie")
    void changePasswordKeepsThisSession() throws Exception {
        MockHttpSession tutaj = potwierdzone("pr_ewa", "ewa.haslo@example.com");
        MockHttpSession gdzieIndziej = zalogowany("pr_ewa");

        mvc.perform(put("/api/profile/password").with(csrf()).session(tutaj)
                .header("Accept-Language", "pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}"
                    .formatted(HASLO, NOWE, NOWE)))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(tutaj)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(gdzieIndziej)).andExpect(status().isUnauthorized());
        assertThat(ostatniaDo("ewa.haslo@example.com").temat()).isEqualTo("Hasło do MusicClub zostało zmienione");
    }
}
