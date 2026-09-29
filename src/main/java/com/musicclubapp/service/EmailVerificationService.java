package com.musicclubapp.service;

import com.musicclubapp.dto.EmailChangeInfoResponse;
import com.musicclubapp.dto.EmailVerificationResponse;
import com.musicclubapp.entity.EmailToken;
import com.musicclubapp.entity.TokenPurpose;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.EmailNotVerifiedException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.EmailTokenRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Potwierdzanie adresu e-mail: przy zakladaniu konta i przy zmianie adresu.
 *
 * <p>Dziala tylko wtedy, gdy serwer ma skonfigurowana poczte (MAIL_HOST).
 * Bez niej nikt nie mialby jak dostac linku, wiec wszyscy licza sie jako
 * potwierdzeni - inaczej nikt nowy nie moglby sie zalogowac.</p>
 *
 * <p>Zmiana adresu wymaga dwoch klikniec, w dowolnej kolejnosci:</p>
 * <ul>
 *   <li>zgody ze STAREGO adresu - bez niej ktos, kto przejal sesje (np. na
 *       cudzym komputerze), podmienilby adres na swoj, a potem przez "nie
 *       pamietam hasla" zabral konto na dobre;</li>
 *   <li>potwierdzenia NOWEGO adresu - ze to prawdziwa skrzynka.</li>
 * </ul>
 * <p>Ze starego adresu mozna tez kliknac "to nie ja": zmiana przepada,
 * a wszystkie urzadzenia zostaja wylogowane.</p>
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /** Strony frontendu, na ktore prowadza linki. */
    static final String POTWIERDZ = "/potwierdz-email";
    static final String ZGODA = "/potwierdz-zmiane-adresu";

    private final AccountLinks links;
    private final UserRepository users;
    private final EmailTokenRepository tokens;

    public EmailVerificationService(AccountLinks links, UserRepository users, EmailTokenRepository tokens) {
        this.links = links;
        this.users = users;
        this.tokens = tokens;
    }

    /** Czy potwierdzanie jest wlaczone - czyli czy serwer wysyla poczte. */
    public boolean enabled() {
        return links.enabled();
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
            if (!links.hasPublicUrl()) {
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
            user.markEmailVerified(links.now());
            return;
        }
        links.checkLimits(user, TokenPurpose.VERIFY);
        links.send(user, user.getEmail(), TokenPurpose.VERIFY, AccountMails.Kind.REGISTRATION, POTWIERDZ, "");
    }

    /**
     * Zmiana adresu w profilu (haslo sprawdza UserService). Bez poczty - od
     * razu. Z poczta - dwie wiadomosci: prosba o zgode na stary adres
     * i potwierdzenie na nowy. Do czasu obu klikniec obowiazuje stary.
     */
    @Transactional
    public void requestChange(User user, String newEmail) {
        if (!enabled()) {
            user.setEmail(newEmail);
            user.clearEmailChange();
            return;
        }
        if (newEmail.equals(user.getPendingEmail())) {
            // Ten sam adres jeszcze raz - wiadomosci juz poszly; ponownie wysyla osobny przycisk
            return;
        }
        links.checkLimits(user, TokenPurpose.VERIFY);
        porzucLinkiZmiany(user);
        user.startEmailChange(newEmail);
        if (!user.isEmailVerified()) {
            // Stary adres nigdy nie byl potwierdzony - nie ma od kogo brac zgody
            user.approveEmailChangeFromOld(links.now());
        }
        wyslijBrakujace(user);
    }

    /** "Wyslij jeszcze raz" przy zmianie adresu - tylko to, czego jeszcze nie kliknieto. */
    @Transactional
    public void resendChange(String username) {
        User user = require(username);
        if (user.getPendingEmail() == null) {
            throw OperationNotAllowedException.noPendingEmail();
        }
        links.checkLimits(user, TokenPurpose.VERIFY);
        wyslijBrakujace(user);
    }

    /** Rezygnacja ze zmiany adresu - zostaje stary, a wyslane linki przestaja dzialac. */
    @Transactional
    public void cancelChange(String username) {
        User user = require(username);
        if (user.getPendingEmail() == null) {
            throw OperationNotAllowedException.noPendingEmail();
        }
        porzucLinkiZmiany(user);
        user.clearEmailChange();
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
        links.checkLimits(user, TokenPurpose.VERIFY);
        String adres = EmailAddresses.normalize(correctedEmail);
        if (StringUtils.hasText(adres) && !adres.equalsIgnoreCase(user.getEmail())) {
            if (users.existsByEmailIgnoreCase(adres)) {
                throw DuplicateResourceException.email(adres);
            }
            // Poprzednie linki szly na zly adres - niech nie zostaja wazne
            tokens.expireAllOfUser(user.getId(), links.now());
            user.setEmail(adres);
        }
        links.send(user, user.getEmail(), TokenPurpose.VERIFY, AccountMails.Kind.REGISTRATION, POTWIERDZ, "");
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

    /**
     * Klikniety link potwierdzajacy. Potwierdza adres, na ktory poszedl -
     * o ile konto wciaz go uzywa albo wlasnie na niego zmienia.
     */
    @Transactional
    public EmailVerificationResponse verify(String rawToken) {
        EmailToken token = links.require(rawToken, TokenPurpose.VERIFY);
        User user = token.getUser();
        String adres = token.getEmail();
        LocalDateTime teraz = links.now();

        if (adres.equalsIgnoreCase(user.getEmail())) {
            if (!user.isEmailVerified()) {
                user.markEmailVerified(teraz);
            }
            tokens.expireByUserIdAndEmail(user.getId(), adres, teraz);
            log.info("Potwierdzono adres e-mail konta {}", user.getUsername());
            return new EmailVerificationResponse(EmailVerificationResponse.Result.VERIFIED,
                user.getUsername(), adres);
        }

        if (adres.equalsIgnoreCase(user.getPendingEmail())) {
            user.verifyPendingEmail(teraz);
            token.expire(teraz);
            return dokonczJesliMozna(user);
        }

        // Adres zmienil sie od wyslania linku - nie potwierdzamy niczego, czego link nie dotyczyl
        throw OperationNotAllowedException.emailTokenInvalid();
    }

    /** Co pokazac na stronie zgody: czyje konto i na jaki adres (zamaskowany). */
    @Transactional(readOnly = true)
    public EmailChangeInfoResponse changeInfo(String rawToken) {
        EmailToken token = wazaZgoda(rawToken);
        User user = token.getUser();
        return new EmailChangeInfoResponse(user.getUsername(), EmailAddresses.mask(user.getPendingEmail()));
    }

    /** Zgoda ze starego adresu. */
    @Transactional
    public EmailVerificationResponse approveChange(String rawToken) {
        EmailToken token = wazaZgoda(rawToken);
        User user = token.getUser();
        user.approveEmailChangeFromOld(links.now());
        token.expire(links.now());
        return dokonczJesliMozna(user);
    }

    /**
     * "To nie ja" ze starego adresu. Ktos inny ma dostep do konta - zmiana
     * przepada, a wszystkie sesje i ciasteczka "zapamietaj mnie" przestaja
     * dzialac. Wlasciciel dostaje na stronie przycisk ustawienia nowego hasla.
     */
    @Transactional
    public EmailVerificationResponse denyChange(String rawToken) {
        EmailToken token = wazaZgoda(rawToken);
        User user = token.getUser();
        porzucLinkiZmiany(user);
        user.clearEmailChange();
        user.rotateSecurityStamp();
        log.warn("Konto {}: zmiana adresu odrzucona ze starej skrzynki - wszystkie sesje wylogowane",
            user.getUsername());
        return new EmailVerificationResponse(EmailVerificationResponse.Result.CHANGE_DENIED,
            user.getUsername(), user.getEmail());
    }

    /** Sprzatanie po koncie - wolane przez AccountDeletionService. */
    @Transactional
    public void deleteAllOf(Long userId) {
        tokens.deleteAllOfUser(userId);
    }

    /** Linki starsze niz doba - wolane przez sprzatanie co godzine. */
    @Transactional
    public int deleteExpired() {
        return tokens.deleteCreatedBefore(links.now().minusDays(1));
    }

    /* ------------------------------------------------------------------ */

    /** Link zgody dotyczy obecnego adresu i trwajacej zmiany - inaczej jest niewazny. */
    private EmailToken wazaZgoda(String rawToken) {
        EmailToken token = links.require(rawToken, TokenPurpose.APPROVE_CHANGE);
        User user = token.getUser();
        if (user.getPendingEmail() == null || !token.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw OperationNotAllowedException.emailTokenInvalid();
        }
        return token;
    }

    /** Obie zgody sa - adres sie zmienia. Brakuje jednej - mowimy ktorej. */
    private EmailVerificationResponse dokonczJesliMozna(User user) {
        String nowy = user.getPendingEmail();
        if (!user.emailChangeComplete()) {
            EmailVerificationResponse.Result czeka = user.getPendingEmailOldApprovedAt() == null
                ? EmailVerificationResponse.Result.WAITING_OLD
                : EmailVerificationResponse.Result.WAITING_NEW;
            return new EmailVerificationResponse(czeka, user.getUsername(), nowy);
        }
        if (users.existsByEmailIgnoreCase(nowy)) {
            throw OperationNotAllowedException.emailTaken();
        }
        porzucLinkiZmiany(user);
        user.setEmail(nowy);
        user.clearEmailChange();
        user.markEmailVerified(links.now());
        log.info("Konto {} zmienilo adres e-mail", user.getUsername());
        return new EmailVerificationResponse(EmailVerificationResponse.Result.CHANGED, user.getUsername(), nowy);
    }

    /** Wysyla to, czego przy trwajacej zmianie jeszcze nie kliknieto. */
    private void wyslijBrakujace(User user) {
        String nowy = user.getPendingEmail();
        if (user.getPendingEmailNewVerifiedAt() == null) {
            links.send(user, nowy, TokenPurpose.VERIFY, AccountMails.Kind.CHANGE_NEW, POTWIERDZ, "");
        }
        if (user.getPendingEmailOldApprovedAt() == null) {
            links.send(user, user.getEmail(), TokenPurpose.APPROVE_CHANGE, AccountMails.Kind.CHANGE_OLD, ZGODA,
                EmailAddresses.mask(nowy));
        }
    }

    /** Linki poprzedniej zmiany adresu przestaja dzialac. */
    private void porzucLinkiZmiany(User user) {
        LocalDateTime teraz = links.now();
        if (user.getPendingEmail() != null) {
            tokens.expireByUserIdAndEmail(user.getId(), user.getPendingEmail(), teraz);
        }
        tokens.expireByUserIdAndPurpose(user.getId(), TokenPurpose.APPROVE_CHANGE, teraz);
    }

    private User require(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
