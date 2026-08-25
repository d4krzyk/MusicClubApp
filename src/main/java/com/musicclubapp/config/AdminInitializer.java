package com.musicclubapp.config;

import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zaklada konto administratora przy pierwszym uruchomieniu aplikacji.
 *
 * <p><b>Po co to?</b> Rejestracja przez formularz zawsze tworzy zwyklego
 * uzytkownika ({@code Role.USER}) - i tak ma byc, bo inaczej kazdy moglby
 * zrobic sobie konto administratora. Musi wiec istniec inna droga do
 * pierwszego admina. Recznie wpisywac go do bazy jest niewygodnie (haslo
 * trzeba by zahashowac BCryptem), dlatego robi to aplikacja przy starcie.</p>
 *
 * <p>{@link CommandLineRunner} uruchamia sie raz, tuz po wstaniu kontekstu
 * Springa. Konto powstaje TYLKO wtedy, gdy w bazie nie ma jeszcze zadnego
 * administratora - kolejne restarty nic nie nadpisuja, wiec zmienione haslo
 * nie wroci do wartosci domyslnej.</p>
 *
 * <p>Dane logowania czytamy z {@code application.properties}. Wartosci
 * domyslne sluza tylko do nauki - przy oddawaniu projektu ustaw wlasne
 * przez zmienne srodowiskowe.</p>
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    /** Haslo, przy ktorym wypisujemy ostrzezenie - zeby nie zostalo na produkcji. */
    private static final String HASLO_DOMYSLNE = "admin12345";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.email:admin@musicclub.local}")
    private String adminEmail;

    @Value("${app.admin.password:" + HASLO_DOMYSLNE + "}")
    private String adminPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        /*
         * Zabezpieczenie przed sytuacja, w ktorej ktos zdazyl zarejestrowac
         * zwykle konto o loginie "admin" - wtedy zapis zlamalby ograniczenie
         * UNIQUE i aplikacja nie wstalaby w ogole.
         */
        if (userRepository.existsByUsername(adminUsername)
            || userRepository.existsByEmail(adminEmail)) {
            log.warn("Nie zalozono konta administratora: login '{}' lub e-mail '{}' "
                + "jest juz zajety przez zwykle konto. Zmien app.admin.username / app.admin.email "
                + "albo nadaj role ADMIN recznie w bazie.", adminUsername, adminEmail);
            return;
        }

        User admin = new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.info("Zalozono konto administratora o loginie '{}'.", adminUsername);

        if (HASLO_DOMYSLNE.equals(adminPassword)) {
            log.warn("""
                UWAGA: administrator uzywa hasla domyslnego ({}).\s
                Zmien je w ustawieniach konta albo ustaw zmienna ADMIN_PASSWORD\s
                przed oddaniem projektu.""", HASLO_DOMYSLNE);
        }
    }
}
