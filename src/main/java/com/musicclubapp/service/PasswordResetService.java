package com.musicclubapp.service;

import com.musicclubapp.dto.PasswordResetInfoResponse;
import com.musicclubapp.entity.EmailToken;
import com.musicclubapp.entity.TokenPurpose;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.error.TooManyRequestsException;
import com.musicclubapp.repository.EmailTokenRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Nie pamietam hasla": link na adres konta, a po kliknieciu nowe haslo.
 *
 * <p>Odpowiedz na prosbe o link jest ZAWSZE taka sama - niezaleznie od tego,
 * czy konto z tym adresem istnieje i czy limit wysylek sie nie wyczerpal.
 * Inaczej formularz resetu bylby wyszukiwarka, kto ma konto w MusicClub.</p>
 *
 * <p>Nowe haslo zmienia znacznik bezpieczenstwa konta: wszystkie inne
 * sesje i ciasteczka "zapamietaj mnie" przestaja dzialac. Kto przejal konto
 * i zmienil haslo, zostaje wylogowany, gdy wlasciciel je odzyska.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final String STRONA = "/nowe-haslo";

    private final AccountLinks links;
    private final UserRepository users;
    private final EmailTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(AccountLinks links, UserRepository users, EmailTokenRepository tokens,
                                PasswordEncoder passwordEncoder) {
        this.links = links;
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean enabled() {
        return links.enabled();
    }

    /**
     * Prosba o link. Nic nie zwraca i niczego nie zdradza - patrz opis klasy.
     * Link idzie tylko na obecny adres konta, nigdy na ten czekajacy na zmiane.
     */
    @Transactional
    public void request(String email) {
        if (!enabled()) {
            throw OperationNotAllowedException.mailDisabled();
        }
        String adres = EmailAddresses.normalize(email);
        User user = users.findFirstByEmailIgnoreCase(adres).orElse(null);
        if (user == null) {
            log.info("Reset hasla: brak konta z adresem {}", EmailAddresses.mask(adres));
            return;
        }
        try {
            links.checkLimits(user, TokenPurpose.PASSWORD_RESET);
        } catch (TooManyRequestsException e) {
            // 429 powiedzialby, ze konto istnieje - po cichu nic nie wysylamy
            log.info("Reset hasla dla {}: limit wysylek wyczerpany", user.getUsername());
            return;
        }
        links.send(user, user.getEmail(), TokenPurpose.PASSWORD_RESET, AccountMails.Kind.PASSWORD_RESET, STRONA, "");
    }

    /** Czy link jest jeszcze wazny - strona pokazuje formularz albo od razu "link wygasl". */
    @Transactional(readOnly = true)
    public PasswordResetInfoResponse check(String rawToken) {
        return new PasswordResetInfoResponse(wazny(rawToken).getUser().getUsername());
    }

    /**
     * Nowe haslo. Klikniecie w link dowodzi dostepu do skrzynki - wiec adres
     * jest przy okazji potwierdzony, jesli jeszcze nie byl.
     */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        EmailToken token = wazny(rawToken);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.rotateSecurityStamp();
        if (!user.isEmailVerified()) {
            user.markEmailVerified(links.now());
        }
        tokens.expireByUserIdAndPurpose(user.getId(), TokenPurpose.PASSWORD_RESET, links.now());
        links.notify(user, AccountMails.Kind.PASSWORD_CHANGED, "/reset-hasla");
        log.info("Konto {}: haslo ustawione z linku resetu, inne sesje wylogowane", user.getUsername());
    }

    /** Link resetu na OBECNY adres konta - po zmianie adresu stare linki nie dzialaja. */
    private EmailToken wazny(String rawToken) {
        EmailToken token = links.require(rawToken, TokenPurpose.PASSWORD_RESET);
        if (!token.getEmail().equalsIgnoreCase(token.getUser().getEmail())) {
            throw OperationNotAllowedException.emailTokenInvalid();
        }
        return token;
    }
}
