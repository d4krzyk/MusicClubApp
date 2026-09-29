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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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
 * Wspolna baza testow poczty: udawany serwer SMTP, ktory tylko zbiera
 * wiadomosci, i skroty do wywolan API. Obie klasy testow uzywaja tej samej
 * konfiguracji, wiec Spring buduje dla nich jeden kontekst.
 *
 * <p>Bez @Transactional: wiadomosc wychodzi dopiero po zatwierdzeniu
 * transakcji, a test w jednej wielkiej transakcji nigdy by jej nie
 * zatwierdzil. Dlatego konta kasujemy recznie po kazdym tescie.</p>
 */
abstract class PocztaTestowa {

    static final String HASLO = "haslo-testowe-1";
    static final Pattern LINK = Pattern.compile(
        "https?://[^\\s\"<]+/(?:potwierdz-email|potwierdz-zmiane-adresu|nowe-haslo)\\?token=([A-Za-z0-9_-]+)");

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

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected Skrzynka skrzynka;
    @Autowired protected UserRepository users;
    @Autowired protected EmailTokenRepository tokens;
    @Autowired protected AccountDeletionService deletion;
    @Autowired protected UnverifiedAccountCleanup cleanup;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager transactionManager;

    protected final List<String> zalozone = new ArrayList<>();

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

    protected ResultActions zarejestruj(String login, String email) throws Exception {
        zalozone.add(login);
        return mvc.perform(post("/api/auth/register").with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of(
                "username", login, "email", email, "password", HASLO, "confirmPassword", HASLO))));
    }

    protected ResultActions zaloguj(String login, String haslo) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + login + "\",\"password\":\"" + haslo + "\"}"));
    }

    protected ResultActions potwierdz(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
    }

    protected ResultActions wyslijPonownie(String login, String haslo, String email) throws Exception {
        String cialo = email == null
            ? "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(login, haslo)
            : "{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\"}".formatted(login, haslo, email);
        return mvc.perform(post("/api/auth/resend-verification").with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(cialo));
    }

    /** Ostatnia wiadomosc: do kogo, temat, obie tresci i token z linku. */
    protected record Wiadomosc(String do_, String temat, String html, String tekst, String token, List<String> obrazki) {
    }

    protected Wiadomosc ostatnia() throws Exception {
        assertThat(skrzynka.wyslane).isNotEmpty();
        return rozloz(skrzynka.wyslane.get(skrzynka.wyslane.size() - 1));
    }

    /** Ostatnia wiadomosc na podany adres - zmiana adresu wysyla dwie naraz. */
    protected Wiadomosc ostatniaDo(String adres) throws Exception {
        for (int i = skrzynka.wyslane.size() - 1; i >= 0; i--) {
            MimeMessage m = skrzynka.wyslane.get(i);
            if (m.getAllRecipients()[0].toString().equals(adres)) {
                return rozloz(m);
            }
        }
        throw new AssertionError("Brak wiadomosci na " + adres);
    }

    /** Ile wiadomosci poszlo na podany adres. */
    protected long ileDo(String adres) throws Exception {
        long ile = 0;
        for (MimeMessage m : List.copyOf(skrzynka.wyslane)) {
            if (m.getAllRecipients()[0].toString().equals(adres)) {
                ile++;
            }
        }
        return ile;
    }

    private static Wiadomosc rozloz(MimeMessage m) throws Exception {
        List<String> html = new ArrayList<>();
        List<String> tekst = new ArrayList<>();
        List<String> obrazki = new ArrayList<>();
        rozbierz(m, html, tekst, obrazki);
        Matcher link = LINK.matcher(html.get(0));
        return new Wiadomosc(m.getAllRecipients()[0].toString(), m.getSubject(), html.get(0), tekst.get(0),
            link.find() ? link.group(1) : null, obrazki);
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

    protected JsonNode cialo(MvcResult wynik) throws Exception {
        return json.readTree(wynik.getResponse().getContentAsString());
    }

    /** Cofa w czasie ostatnio wyslane linki - zamiast czekac minute na kolejna wysylke. */
    protected void cofnijWysylki(String login, int sekund) {
        User u = users.findByUsername(login).orElseThrow();
        jdbc.update("UPDATE email_tokens SET created_at = ? WHERE user_id = ?",
            LocalDateTime.now().minusSeconds(sekund), u.getId());
    }

    protected MockHttpSession zalogowany(String login) throws Exception {
        return (MockHttpSession) zaloguj(login, HASLO).andExpect(status().isOk())
            .andReturn().getRequest().getSession();
    }

    /** Konto z potwierdzonym adresem, od razu zalogowane. */
    protected MockHttpSession potwierdzone(String login, String email) throws Exception {
        zarejestruj(login, email).andExpect(status().isCreated());
        potwierdz(ostatniaDo(email).token()).andExpect(status().isOk());
        return zalogowany(login);
    }

    protected ResultActions postJson(String adres, String cialo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(adres).with(csrf())
            .header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(cialo));
    }
}
