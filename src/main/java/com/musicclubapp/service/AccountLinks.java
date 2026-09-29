package com.musicclubapp.service;

import com.musicclubapp.entity.EmailToken;
import com.musicclubapp.entity.TokenPurpose;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.repository.EmailTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Linki w wiadomosciach o koncie: tworzenie, limity wysylek i sprawdzanie
 * przy kliknieciu. Wspolne dla potwierdzania adresu i resetu hasla, zeby
 * zasady (skrot w bazie, jednorazowosc, limity) byly w jednym miejscu.
 */
@Component
public class AccountLinks {

    static final Duration WAZNOSC = Duration.ofHours(24);
    static final Duration WAZNOSC_RESETU = Duration.ofHours(1);
    static final Duration ODSTEP = Duration.ofSeconds(60);
    static final int NA_DOBE = 5;

    private static final SecureRandom LOSOWANIE = new SecureRandom();

    private final MailService mail;
    private final AccountMails templates;
    private final EmailTokenRepository tokens;
    private final Clock clock;
    private final String publicUrl;

    public AccountLinks(MailService mail,
                        AccountMails templates,
                        EmailTokenRepository tokens,
                        Clock clock,
                        @Value("${app.public-url:}") String publicUrl) {
        this.mail = mail;
        this.templates = templates;
        this.tokens = tokens;
        this.clock = clock;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
    }

    /** Czy serwer w ogole wysyla poczte. */
    public boolean enabled() {
        return mail.configured();
    }

    /** Czy link ma ustalony adres strony - bez tego bierzemy go z zapytania. */
    boolean hasPublicUrl() {
        return StringUtils.hasText(publicUrl);
    }

    /**
     * Rzuca 429, jesli ta osoba dostala wiadomosc tego rodzaju przed chwila
     * albo wyczerpala dobowy limit. Wolane RAZ na akcje - zmiana adresu
     * wysyla dwie wiadomosci naraz i obie licza sie jako jedna.
     */
    public void checkLimits(User user, TokenPurpose purpose) {
        if (user.getId() == null) {
            return;
        }
        LocalDateTime teraz = now();
        tokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose).ifPresent(ostatni -> {
            long minelo = Duration.between(ostatni.getCreatedAt(), teraz).toSeconds();
            if (minelo < ODSTEP.toSeconds()) {
                throw new TooManyRequestsException(ODSTEP.toSeconds() - minelo);
            }
        });
        LocalDateTime dobaTemu = teraz.minusDays(1);
        if (tokens.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, dobaTemu) >= NA_DOBE) {
            long czekaj = tokens.findFirstByUserIdAndPurposeAndCreatedAtAfterOrderByCreatedAtAsc(
                    user.getId(), purpose, dobaTemu)
                .map(najstarszy -> Duration.between(teraz, najstarszy.getCreatedAt().plusDays(1)).toSeconds())
                .orElse(3600L);
            throw new TooManyRequestsException(Math.max(60, czekaj));
        }
    }

    /**
     * Zapisuje nowy link i wysyla wiadomosc po zatwierdzeniu transakcji.
     * Limity sprawdza {@link #checkLimits} - wolane wczesniej, osobno.
     *
     * @param sciezka strona frontendu, np. "/potwierdz-email"
     * @param dodatek drugi argument zdania wstepnego w wiadomosci (moze byc pusty)
     */
    public void send(User user, String adres, TokenPurpose purpose, AccountMails.Kind rodzaj,
                     String sciezka, String dodatek) {
        LocalDateTime teraz = now();
        String token = nowyToken();
        Duration waznosc = purpose == TokenPurpose.PASSWORD_RESET ? WAZNOSC_RESETU : WAZNOSC;
        tokens.save(new EmailToken(user, purpose, hash(token), adres, teraz, teraz.plus(waznosc)));

        String link = adresStrony() + sciezka + "?token=" + token;
        mail.sendAfterCommit(templates.compose(rodzaj, adres, user.getUsername(), link, dodatek,
            LocaleContextHolder.getLocale()));
    }

    /** Powiadomienie bez linku jednorazowego - przycisk prowadzi na zwykla strone aplikacji. */
    public void notify(User user, AccountMails.Kind rodzaj, String sciezka) {
        String link = adresStrony() + sciezka;
        mail.sendAfterCommit(templates.compose(rodzaj, user.getEmail(), user.getUsername(), link, "",
            LocaleContextHolder.getLocale()));
    }

    /**
     * Link z klikniecia: istnieje, jest tego rodzaju i nie wygasl. Wszystko
     * inne to ten sam blad - bez podpowiedzi, co dokladnie bylo nie tak.
     */
    public EmailToken require(String rawToken, TokenPurpose purpose) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 100) {
            throw OperationNotAllowedException.emailTokenInvalid();
        }
        EmailToken token = tokens.findByHash(hash(rawToken.trim()))
            .orElseThrow(OperationNotAllowedException::emailTokenInvalid);
        if (token.getPurpose() != purpose || token.isExpired(now())) {
            throw OperationNotAllowedException.emailTokenInvalid();
        }
        return token;
    }

    /**
     * Skad link ma prowadzic. APP_PUBLIC_URL, jesli jest - wtedy nic z zapytania
     * nie wplywa na adres w wiadomosci. Bez niego: adres, pod ktorym przyszlo
     * zapytanie (za nginxem - z naglowkow X-Forwarded-*, ktore nginx ustawia sam).
     */
    private String adresStrony() {
        if (StringUtils.hasText(publicUrl)) {
            return publicUrl;
        }
        if (RequestContextHolder.getRequestAttributes() != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        }
        return "";
    }

    /** Czas w strefie serwera - tak jak data zalozenia konta. */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }

    /** 32 losowe bajty - nie do zgadniecia, a w adresie tylko litery, cyfry, "-" i "_". */
    static String nowyToken() {
        byte[] bajty = new byte[32];
        LOSOWANIE.nextBytes(bajty);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bajty);
    }

    static String hash(String token) {
        try {
            byte[] skrot = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(skrot);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Brak SHA-256", e);
        }
    }
}
