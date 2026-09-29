package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Potwierdzanie adresu e-mail od poczatku do konca: przez prawdziwe API,
 * Spring Security i baze - tylko serwer SMTP jest udawany i zbiera wiadomosci.
 */
@SpringBootTest(properties = "app.mail.per-ip-per-hour=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PocztaTestowa.Poczta.class)
@DisplayName("Potwierdzanie adresu e-mail - caly przebieg")
class EmailVerificationFlowTest extends PocztaTestowa {

    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("rejestracja wysyla estetyczna wiadomosc z linkiem; bez klikniecia nie da sie zalogowac")
    void registrationSendsLinkAndBlocksLogin() throws Exception {
        zarejestruj("mv_ola", "Ola.Nowak@Example.com")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(jsonPath("$.email").value("ola.nowak@example.com"));

        Wiadomosc w = ostatnia();
        assertThat(w.do_()).isEqualTo("ola.nowak@example.com");
        assertThat(w.temat()).isEqualTo("Potwierdź adres e-mail w MusicClub");
        assertThat(w.html()).contains("Cześć, mv_ola!", "Potwierdź adres e-mail", "cid:napis")
            .doesNotContain("{{", "<!--");
        assertThat(w.tekst()).contains("/potwierdz-email?token=" + w.token());
        assertThat(w.obrazki()).singleElement().asString().contains("<napis>", "image/png");
        assertThat(skrzynka.wyslane.get(0).getFrom()[0].toString()).isEqualTo("MusicClub <no-reply@musicclub.test>");

        // W bazie jest skrot, a nie sam token
        assertThat(jdbc.queryForList("SELECT token_hash FROM email_tokens", String.class))
            .doesNotContain(w.token()).contains(AccountLinks.hash(w.token()));

        // Zle haslo - zwykle 401, bez zdradzania, ze konto czeka na potwierdzenie
        zaloguj("mv_ola", "zle-haslo-123").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").doesNotExist());
        // Dobre haslo - 403 z kodem i zamaskowanym adresem
        zaloguj("mv_ola", HASLO).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("o***k@example.com")));
        // ...i sesja nie powstala
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        potwierdz(w.token()).andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("VERIFIED"))
            .andExpect(jsonPath("$.username").value("mv_ola"));
        zaloguj("mv_ola", HASLO).andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));

        // Link jest jednorazowy
        potwierdz(w.token()).andExpect(status().isConflict());
        potwierdz("zmyslony-token").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ten sam adres innymi literami to ten sam adres; skrzynki jednorazowe odpadaja")
    void emailCaseAndDisposable() throws Exception {
        zarejestruj("mv_ala", "ala@example.com").andExpect(status().isCreated());
        zarejestruj("mv_ala2", "ALA@Example.com").andExpect(status().isConflict());
        zarejestruj("mv_troll", "troll@mailinator.com").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("email"));
        zarejestruj("mv_troll2", "troll@abc.yopmail.com").andExpect(status().isUnprocessableEntity());
        assertThat(skrzynka.wyslane).hasSize(1);
    }

    @Test
    @DisplayName("ponowna wysylka: z haslem, najwyzej raz na minute, z poprawka literowki w adresie")
    void resend() throws Exception {
        zarejestruj("mv_bartek", "bartek@gmial.com").andExpect(status().isCreated());
        String pierwszy = ostatnia().token();

        wyslijPonownie("mv_bartek", "zle-haslo-123", null).andExpect(status().isUnauthorized());
        wyslijPonownie("mv_bartek", HASLO, null).andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
        assertThat(skrzynka.wyslane).hasSize(1);

        cofnijWysylki("mv_bartek", 120);
        wyslijPonownie("mv_bartek", HASLO, "bartek@gmail.com").andExpect(status().isNoContent());
        Wiadomosc druga = ostatnia();
        assertThat(druga.do_()).isEqualTo("bartek@gmail.com");
        assertThat(users.findByUsername("mv_bartek").orElseThrow().getEmail()).isEqualTo("bartek@gmail.com");

        // Link wyslany na literowke juz niczego nie potwierdza
        potwierdz(pierwszy).andExpect(status().isConflict());
        potwierdz(druga.token()).andExpect(status().isOk());

        // Potwierdzony - kolejna wysylka nie ma sensu
        cofnijWysylki("mv_bartek", 120);
        wyslijPonownie("mv_bartek", HASLO, null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("najwyzej piec wiadomosci na dobe na konto")
    void dailyLimit() throws Exception {
        zarejestruj("mv_czesiek", "czesiek@example.com").andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            cofnijWysylki("mv_czesiek", 120);
            wyslijPonownie("mv_czesiek", HASLO, null).andExpect(status().isNoContent());
        }
        cofnijWysylki("mv_czesiek", 120);
        wyslijPonownie("mv_czesiek", HASLO, null).andExpect(status().isTooManyRequests());
        assertThat(skrzynka.wyslane).hasSize(5);
    }

    @Test
    @DisplayName("przeterminowany link nie dziala")
    void expiredLink() throws Exception {
        zarejestruj("mv_dorota", "dorota@example.com").andExpect(status().isCreated());
        String token = ostatnia().token();
        jdbc.update("UPDATE email_tokens SET expires_at = ?", LocalDateTime.now().minusMinutes(1));

        potwierdz(token).andExpect(status().isConflict());
        assertThat(users.findByUsername("mv_dorota").orElseThrow().isEmailVerified()).isFalse();
    }

    private ResultActions zmienAdres(MockHttpSession sesja, String login, String email, String haslo)
            throws Exception {
        String cialo = haslo == null
            ? "{\"username\":\"%s\",\"email\":\"%s\"}".formatted(login, email)
            : "{\"username\":\"%s\",\"email\":\"%s\",\"currentPassword\":\"%s\"}".formatted(login, email, haslo);
        return mvc.perform(put("/api/profile").with(csrf()).session(sesja)
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(cialo));
    }

    @Test
    @DisplayName("zmiana adresu: haslo, zgoda ze starego adresu i potwierdzenie nowego - w dowolnej kolejnosci")
    void emailChangeNeedsPasswordAndBothAddresses() throws Exception {
        MockHttpSession sesja = potwierdzone("mv_ewa", "ewa@example.com");
        cofnijWysylki("mv_ewa", 120);

        // Bez hasla i ze zlym haslem - nic sie nie dzieje
        zmienAdres(sesja, "mv_ewa", "ewa.nowa@example.com", null).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        zmienAdres(sesja, "mv_ewa", "ewa.nowa@example.com", "zle-haslo-1").andExpect(status().isUnprocessableEntity());
        assertThat(ileDo("ewa.nowa@example.com")).isZero();

        MvcResult zmiana = zmienAdres(sesja, "mv_ewa", "ewa.nowa@example.com", HASLO)
            .andExpect(status().isOk()).andReturn();
        assertThat(cialo(zmiana).get("email").asText()).isEqualTo("ewa@example.com");
        assertThat(cialo(zmiana).get("pendingEmail").asText()).isEqualTo("ewa.nowa@example.com");

        Wiadomosc nowy = ostatniaDo("ewa.nowa@example.com");
        Wiadomosc stary = ostatniaDo("ewa@example.com");
        assertThat(nowy.temat()).isEqualTo("Potwierdź nowy adres e-mail w MusicClub");
        assertThat(stary.temat()).isEqualTo("Czy to Ty zmieniasz adres e-mail w MusicClub?");
        assertThat(stary.html()).contains("mv_ewa", "e***a@example.com", "/potwierdz-zmiane-adresu?token=")
            .doesNotContain("ewa.nowa@example.com");

        // Nowy potwierdzony - ale bez zgody ze starego adres sie nie zmienia
        potwierdz(nowy.token()).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("WAITING_OLD"));
        mvc.perform(get("/api/auth/me").session(sesja)).andExpect(jsonPath("$.email").value("ewa@example.com"));

        // Strona zgody pokazuje zamaskowany nowy adres; zgoda konczy zmiane
        postJson("/api/auth/email-change/info", "{\"token\":\"" + stary.token() + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.newEmail").value("e***a@example.com"));
        postJson("/api/auth/email-change/approve", "{\"token\":\"" + stary.token() + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("CHANGED"));
        mvc.perform(get("/api/auth/me").session(sesja))
            .andExpect(jsonPath("$.email").value("ewa.nowa@example.com"))
            .andExpect(jsonPath("$.pendingEmail").doesNotExist());

        // Oba linki sa jednorazowe
        postJson("/api/auth/email-change/approve", "{\"token\":\"" + stary.token() + "\"}")
            .andExpect(status().isConflict());
        potwierdz(nowy.token()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("\"to nie ja\" ze starego adresu: zmiana przepada, wszystkie sesje wylogowane")
    void emailChangeDenied() throws Exception {
        MockHttpSession wlasciciel = potwierdzone("mv_franek", "franek@example.com");
        MockHttpSession zlodziej = zalogowany("mv_franek");
        cofnijWysylki("mv_franek", 120);

        zmienAdres(zlodziej, "mv_franek", "zlodziej@example.com", HASLO).andExpect(status().isOk());
        Wiadomosc nowy = ostatniaDo("zlodziej@example.com");
        Wiadomosc stary = ostatniaDo("franek@example.com");

        postJson("/api/auth/email-change/deny", "{\"token\":\"" + stary.token() + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("CHANGE_DENIED"));

        // Link wyslany na adres zlodzieja nic juz nie zmieni
        potwierdz(nowy.token()).andExpect(status().isConflict());
        assertThat(users.findByUsername("mv_franek").orElseThrow().getEmail()).isEqualTo("franek@example.com");
        // Obie sesje wylogowane - takze ta, z ktorej zaczeto zmiane
        mvc.perform(get("/api/auth/me").session(zlodziej)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(wlasciciel)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rezygnacja ze zmiany uniewaznia oba linki - i nie zeruje limitu wysylek")
    void cancelledChangeKeepsLimits() throws Exception {
        MockHttpSession sesja = potwierdzone("mv_gosia", "gosia@example.com");
        cofnijWysylki("mv_gosia", 120);

        zmienAdres(sesja, "mv_gosia", "gosia.nowa@example.com", HASLO).andExpect(status().isOk());
        Wiadomosc nowy = ostatniaDo("gosia.nowa@example.com");
        Wiadomosc stary = ostatniaDo("gosia@example.com");
        mvc.perform(delete("/api/profile/email/pending").with(csrf()).session(sesja))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pendingEmail").doesNotExist());

        potwierdz(nowy.token()).andExpect(status().isConflict());
        postJson("/api/auth/email-change/approve", "{\"token\":\"" + stary.token() + "\"}")
            .andExpect(status().isConflict());

        // Od razu kolejna zmiana - wciaz obowiazuje minuta odstepu, mimo rezygnacji
        zmienAdres(sesja, "mv_gosia", "gosia.inna@example.com", HASLO).andExpect(status().isTooManyRequests());
        assertThat(ileDo("gosia.inna@example.com")).isZero();
    }

    @Test
    @DisplayName("konto bez potwierdzenia znika po tygodniu - adres wraca do puli")
    void unverifiedAccountsAreRemoved() throws Exception {
        zarejestruj("mv_stary", "stary@example.com").andExpect(status().isCreated());
        zarejestruj("mv_nowy", "nowy@example.com").andExpect(status().isCreated());
        jdbc.update("UPDATE users SET created_at = ? WHERE username = 'mv_stary'",
            LocalDateTime.now().minusDays(8));

        cleanup.clean();

        assertThat(users.findByUsername("mv_stary")).isEmpty();
        assertThat(users.findByUsername("mv_nowy")).isPresent();
        assertThat(tokens.count()).isEqualTo(1);
        // Adres jest znow wolny
        zarejestruj("mv_wlasciciel", "stary@example.com").andExpect(status().isCreated());
    }
}
