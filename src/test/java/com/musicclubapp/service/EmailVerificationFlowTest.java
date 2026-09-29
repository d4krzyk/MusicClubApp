package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.EmailTokenRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Potwierdzanie adresu e-mail od poczatku do konca: przez prawdziwe API,
 * Spring Security i baze - tylko serwer SMTP jest udawany i zbiera wiadomosci.
 *
 * <p>Bez @Transactional na klasie: wiadomosc wychodzi dopiero po zatwierdzeniu
 * transakcji, a test w jednej wielkiej transakcji nigdy by jej nie zatwierdzil.
 * Dlatego konta kasujemy recznie po kazdym tescie.</p>
 */
@SpringBootTest(properties = "app.mail.per-ip-per-hour=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Potwierdzanie adresu e-mail - caly przebieg")
class EmailVerificationFlowTest {

    private static final String HASLO = "haslo-testowe-1";
    private static final Pattern LINK = Pattern.compile("https?://[^\\s\"<]+/potwierdz-email\\?token=([A-Za-z0-9_-]+)");

    /** Udawany serwer SMTP: niczego nie wysyla, tylko zapamietuje. */
    static class Skrzynka extends JavaMailSenderImpl {
        final List<MimeMessage> wyslane = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            for (MimeMessage m : mimeMessages) {
                try {
                    // Prawdziwa wysylka robi to sama - ustala naglowki typow czesci
                    m.saveChanges();
                } catch (MessagingException e) {
                    throw new IllegalStateException(e);
                }
                wyslane.add(m);
            }
        }
    }

    @TestConfiguration
    static class Poczta {
        @Bean
        Skrzynka skrzynka() {
            return new Skrzynka();
        }

        /** Poczta "skonfigurowana", wysylka od razu w tym samym watku - bez czekania w testach. */
        @Bean
        @Primary
        MailService mailService(Skrzynka skrzynka) {
            return new MailService(new ObjectProvider<>() {
                @Override
                public JavaMailSender getObject() {
                    return skrzynka;
                }

                @Override
                public JavaMailSender getIfAvailable() {
                    return skrzynka;
                }

                @Override
                public JavaMailSender getObject(Object... args) {
                    return skrzynka;
                }

                @Override
                public JavaMailSender getIfUnique() {
                    return skrzynka;
                }
            }, "smtp.test", "MusicClub <no-reply@musicclub.test>", Runnable::run);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private Skrzynka skrzynka;
    @Autowired private UserRepository users;
    @Autowired private EmailTokenRepository tokens;
    @Autowired private AccountDeletionService deletion;
    @Autowired private UnverifiedAccountCleanup cleanup;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<String> zalozone = new ArrayList<>();

    @BeforeEach
    void setUp() {
        skrzynka.wyslane.clear();
    }

    @AfterEach
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (String login : zalozone) {
            tx.executeWithoutResult(s -> users.findByUsername(login).ifPresent(deletion::erase));
        }
    }

    /* ------------------------------------------------------------------ */

    private ResultActions zarejestruj(String login, String email) throws Exception {
        zalozone.add(login);
        return mvc.perform(post("/api/auth/register").with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of(
                "username", login, "email", email, "password", HASLO, "confirmPassword", HASLO))));
    }

    private ResultActions zaloguj(String login, String haslo) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + login + "\",\"password\":\"" + haslo + "\"}"));
    }

    private ResultActions potwierdz(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions wyslijPonownie(String login, String haslo, String email) throws Exception {
        String cialo = email == null
            ? "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(login, haslo)
            : "{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\"}".formatted(login, haslo, email);
        return mvc.perform(post("/api/auth/resend-verification").with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(cialo));
    }

    /** Ostatnia wiadomosc: do kogo, temat, obie tresci i token z linku. */
    private record Wiadomosc(String do_, String temat, String html, String tekst, String token, List<String> obrazki) {
    }

    private Wiadomosc ostatnia() throws Exception {
        assertThat(skrzynka.wyslane).isNotEmpty();
        MimeMessage m = skrzynka.wyslane.get(skrzynka.wyslane.size() - 1);
        List<String> html = new ArrayList<>();
        List<String> tekst = new ArrayList<>();
        List<String> obrazki = new ArrayList<>();
        rozbierz(m, html, tekst, obrazki);
        Matcher link = LINK.matcher(html.get(0));
        assertThat(link.find()).as("link w HTML").isTrue();
        return new Wiadomosc(m.getAllRecipients()[0].toString(), m.getSubject(), html.get(0), tekst.get(0),
            link.group(1), obrazki);
    }

    private static void rozbierz(Part czesc, List<String> html, List<String> tekst, List<String> obrazki)
            throws MessagingException, IOException {
        if (czesc.isMimeType("text/html")) {
            html.add((String) czesc.getContent());
        } else if (czesc.isMimeType("text/plain")) {
            tekst.add((String) czesc.getContent());
        } else if (czesc.isMimeType("multipart/*")) {
            Multipart wiele = (Multipart) czesc.getContent();
            for (int i = 0; i < wiele.getCount(); i++) {
                rozbierz(wiele.getBodyPart(i), html, tekst, obrazki);
            }
        } else if (czesc instanceof BodyPart bp && bp.getHeader("Content-ID") != null) {
            obrazki.add(bp.getHeader("Content-ID")[0] + " " + czesc.getContentType());
        }
    }

    private JsonNode cialo(MvcResult wynik) throws Exception {
        return json.readTree(wynik.getResponse().getContentAsString());
    }

    /** Cofa w czasie ostatnio wyslane linki - zamiast czekac minute na kolejna wysylke. */
    private void cofnijWysylki(String login, int sekund) {
        User u = users.findByUsername(login).orElseThrow();
        jdbc.update("UPDATE email_tokens SET created_at = ? WHERE user_id = ?",
            LocalDateTime.now().minusSeconds(sekund), u.getId());
    }

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
            .doesNotContain(w.token()).contains(EmailVerificationService.hash(w.token()));

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

    @Test
    @DisplayName("zmiana adresu w profilu: stary obowiazuje do klikniecia, rezygnacja uniewaznia link")
    void emailChange() throws Exception {
        zarejestruj("mv_ewa", "ewa@example.com").andExpect(status().isCreated());
        potwierdz(ostatnia().token()).andExpect(status().isOk());
        MockHttpSession sesja = (MockHttpSession) zaloguj("mv_ewa", HASLO).andExpect(status().isOk())
            .andReturn().getRequest().getSession();

        cofnijWysylki("mv_ewa", 120);
        MvcResult zmiana = mvc.perform(put("/api/profile").with(csrf()).session(sesja)
                .header("Accept-Language", "pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mv_ewa\",\"email\":\"ewa.nowa@example.com\"}"))
            .andExpect(status().isOk()).andReturn();
        assertThat(cialo(zmiana).get("email").asText()).isEqualTo("ewa@example.com");
        assertThat(cialo(zmiana).get("pendingEmail").asText()).isEqualTo("ewa.nowa@example.com");

        Wiadomosc w = ostatnia();
        assertThat(w.do_()).isEqualTo("ewa.nowa@example.com");
        assertThat(w.temat()).isEqualTo("Potwierdź nowy adres e-mail w MusicClub");

        // Rezygnacja - link przestaje dzialac, adres zostaje stary
        mvc.perform(delete("/api/profile/email/pending").with(csrf()).session(sesja))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pendingEmail").doesNotExist());
        potwierdz(w.token()).andExpect(status().isConflict());

        // Jeszcze raz - i tym razem klikniecie
        cofnijWysylki("mv_ewa", 120);
        mvc.perform(put("/api/profile").with(csrf()).session(sesja)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mv_ewa\",\"email\":\"ewa.nowa@example.com\"}"))
            .andExpect(status().isOk());
        potwierdz(ostatnia().token()).andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("CHANGED"));
        mvc.perform(get("/api/auth/me").session(sesja))
            .andExpect(jsonPath("$.email").value("ewa.nowa@example.com"))
            .andExpect(jsonPath("$.pendingEmail").doesNotExist());
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
