package com.musicclubapp.service;

import com.musicclubapp.dto.EmailVerificationResponse;
import com.musicclubapp.entity.EmailToken;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.EmailNotVerifiedException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.repository.EmailTokenRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * Potwierdzanie adresu e-mail: przy zakladaniu konta i przy zmianie adresu.
 *
 * <p>Dziala tylko wtedy, gdy serwer ma skonfigurowana poczte (MAIL_HOST).
 * Bez niej nikt nie mialby jak dostac linku, wiec wszyscy licza sie jako
 * potwierdzeni - inaczej nikt nowy nie moglby sie zalogowac.</p>
 *
 * <p>Zasady:</p>
 * <ul>
 *   <li>bez potwierdzenia nie da sie zalogowac - sprawdzane PO hasle,</li>
 *   <li>link wazny {@link #WAZNOSC}, jednorazowy, w bazie tylko jego skrot,</li>
 *   <li>kolejna wiadomosc najwczesniej po {@link #ODSTEP} i najwyzej
 *       {@link #NA_DOBE} na dobe na konto,</li>
 *   <li>przy zmianie adresu do czasu klikniecia obowiazuje stary.</li>
 * </ul>
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    static final Duration WAZNOSC = Duration.ofHours(24);
    static final Duration ODSTEP = Duration.ofSeconds(60);
    static final int NA_DOBE = 5;

    /** Adres strony w linku - token jest w parametrze, a te strone obsluguje frontend. */
    static final String SCIEZKA = "/potwierdz-email?token=";

    private static final SecureRandom LOSOWANIE = new SecureRandom();

    private final MailService mail;
    private final VerificationMails templates;
    private final UserRepository users;
    private final EmailTokenRepository tokens;
    private final Clock clock;
    private final String publicUrl;

    public EmailVerificationService(MailService mail,
                                    VerificationMails templates,
                                    UserRepository users,
                                    EmailTokenRepository tokens,
                                    Clock clock,
                                    @Value("${app.public-url:}") String publicUrl) {
        this.mail = mail;
        this.templates = templates;
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
    }

    /** Czy potwierdzanie jest wlaczone - czyli czy serwer wysyla poczte. */
    public boolean enabled() {
        return mail.configured();
    }

    /**
     * Przy starcie bez poczty: kto jeszcze nie ma potwierdzenia, dostaje je
     * z data zalozenia konta. Dzieki temu wlaczenie poczty pozniej nie
     * zablokuje kont zalozonych wczesniej - potwierdzac beda tylko nowi.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void grandfatherWhenDisabled() {
        if (enabled()) {
            if (!StringUtils.hasText(publicUrl)) {
                log.warn("Poczta wlaczona, ale bez APP_PUBLIC_URL - adres w linkach wezmiemy z zapytania");
            }
            return;
        }
        int ile = users.verifyAllUnverified();
        if (ile > 0) {
            log.info("Poczta wylaczona - {} kont uznanych za potwierdzone", ile);
        }
    }

    /** Swiezo zalozone konto: link na podany adres albo - bez poczty - od razu potwierdzone. */
    @Transactional
    public void afterRegistration(User user) {
        if (!enabled()) {
            user.markEmailVerified(now());
            return;
        }
        sendLink(user, user.getEmail(), VerificationMails.Kind.REGISTRATION);
    }

    /**
     * Zmiana adresu w profilu. Bez poczty - od razu. Z poczta - nowy adres
     * czeka na klikniecie, a do tego czasu obowiazuje stary.
     */
    @Transactional
    public void requestChange(User user, String newEmail) {
        if (!enabled()) {
            user.setEmail(newEmail);
            user.setPendingEmail(null);
            return;
        }
        if (newEmail.equals(user.getPendingEmail())) {
            // Ten sam adres jeszcze raz - link juz poszedl; ponownie wysyla osobny przycisk
            return;
        }
        user.setPendingEmail(newEmail);
        sendLink(user, newEmail, VerificationMails.Kind.CHANGE);
    }

    /** "Wyslij jeszcze raz" przy zmianie adresu - z ustawien, po zalogowaniu. */
    @Transactional
    public void resendChange(String username) {
        User user = require(username);
        if (user.getPendingEmail() == null) {
            throw OperationNotAllowedException.noPendingEmail();
        }
        sendLink(user, user.getPendingEmail(), VerificationMails.Kind.CHANGE);
    }

    /** Rezygnacja ze zmiany adresu - zostaje stary, a wyslany link przestaje dzialac. */
    @Transactional
    public void cancelChange(String username) {
        User user = require(username);
        if (user.getPendingEmail() == null) {
            throw OperationNotAllowedException.noPendingEmail();
        }
        tokens.deleteByUserIdAndEmail(user.getId(), user.getPendingEmail());
        user.setPendingEmail(null);
    }

    /**
     * "Wyslij link ponownie" przed pierwszym zalogowaniem. Haslo sprawdza
     * kontroler - tutaj trafia juz ktos, kto je zna. Moze przy okazji
     * poprawic literowke w adresie: konto i tak nie jest jeszcze potwierdzone.
     */
    @Transactional
    public void resendRegistration(String username, String correctedEmail) {
        if (!enabled()) {
            throw OperationNotAllowedException.emailAlreadyVerified();
        }
        User user = require(username);
        if (user.isEmailVerified()) {
            throw OperationNotAllowedException.emailAlreadyVerified();
        }
        String adres = EmailAddresses.normalize(correctedEmail);
        if (StringUtils.hasText(adres) && !adres.equalsIgnoreCase(user.getEmail())) {
            if (users.existsByEmailIgnoreCase(adres)) {
                throw DuplicateResourceException.email(adres);
            }
            // Poprzednie linki szly na zly adres - niech nie zostaja wazne
            tokens.deleteAllOfUser(user.getId());
            user.setEmail(adres);
        }
        sendLink(user, user.getEmail(), VerificationMails.Kind.REGISTRATION);
    }

    /** Logowanie przepuszcza tylko potwierdzonych - wolane PO sprawdzeniu hasla. */
    @Transactional(readOnly = true)
    public void requireVerified(String username) {
        if (!enabled()) {
            return;
        }
        User user = require(username);
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(EmailAddresses.mask(user.getEmail()));
        }
    }

    /** Klikniety link. Potwierdza adres, na ktory poszedl - o ile konto wciaz go uzywa. */
    @Transactional
    public EmailVerificationResponse verify(String rawToken) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 100) {
            throw OperationNotAllowedException.emailTokenInvalid();
        }
        EmailToken token = tokens.findByHash(hash(rawToken.trim()))
            .orElseThrow(OperationNotAllowedException::emailTokenInvalid);
        LocalDateTime teraz = now();
        if (token.isExpired(teraz)) {
            throw OperationNotAllowedException.emailTokenInvalid();
        }

        User user = token.getUser();
        String adres = token.getEmail();

        if (adres.equalsIgnoreCase(user.getEmail())) {
            if (!user.isEmailVerified()) {
                user.markEmailVerified(teraz);
            }
            tokens.deleteByUserIdAndEmail(user.getId(), adres);
            log.info("Potwierdzono adres e-mail konta {}", user.getUsername());
            return new EmailVerificationResponse(EmailVerificationResponse.Result.VERIFIED,
                user.getUsername(), adres);
        }

        if (adres.equalsIgnoreCase(user.getPendingEmail())) {
            if (users.existsByEmailIgnoreCase(adres)) {
                throw OperationNotAllowedException.emailTaken();
            }
            user.setEmail(adres);
            user.setPendingEmail(null);
            user.markEmailVerified(teraz);
            tokens.deleteByUserIdAndEmail(user.getId(), adres);
            log.info("Konto {} zmienilo adres e-mail", user.getUsername());
            return new EmailVerificationResponse(EmailVerificationResponse.Result.CHANGED,
                user.getUsername(), adres);
        }

        // Adres zmienil sie od wyslania linku - nie potwierdzamy niczego, czego link nie dotyczyl
        throw OperationNotAllowedException.emailTokenInvalid();
    }

    /** Sprzatanie po koncie - wolane przez AccountDeletionService. */
    @Transactional
    public void deleteAllOf(Long userId) {
        tokens.deleteAllOfUser(userId);
    }

    /** Przeterminowane linki - wolane przez sprzatanie co godzine. */
    @Transactional
    public int deleteExpired() {
        return tokens.deleteExpiredBefore(now());
    }

    /* ------------------------------------------------------------------ */

    private void sendLink(User user, String adres, VerificationMails.Kind rodzaj) {
        LocalDateTime teraz = now();
        pilnujLimitow(user, teraz);

        String token = nowyToken();
        tokens.save(new EmailToken(user, hash(token), adres, teraz, teraz.plus(WAZNOSC)));

        String link = adresStrony() + SCIEZKA + token;
        mail.sendAfterCommit(templates.compose(rodzaj, adres, user.getUsername(), link,
            LocaleContextHolder.getLocale()));
    }

    private void pilnujLimitow(User user, LocalDateTime teraz) {
        if (user.getId() == null) {
            return;
        }
        tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).ifPresent(ostatni -> {
            long minelo = Duration.between(ostatni.getCreatedAt(), teraz).toSeconds();
            if (minelo < ODSTEP.toSeconds()) {
                throw new TooManyRequestsException(ODSTEP.toSeconds() - minelo);
            }
        });
        LocalDateTime dobaTemu = teraz.minusDays(1);
        if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), dobaTemu) >= NA_DOBE) {
            long czekaj = tokens.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(user.getId(), dobaTemu)
                .map(najstarszy -> Duration.between(teraz, najstarszy.getCreatedAt().plusDays(1)).toSeconds())
                .orElse(3600L);
            throw new TooManyRequestsException(Math.max(60, czekaj));
        }
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

    private User require(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }

    /** Czas w strefie serwera - tak jak data zalozenia konta. */
    private LocalDateTime now() {
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
