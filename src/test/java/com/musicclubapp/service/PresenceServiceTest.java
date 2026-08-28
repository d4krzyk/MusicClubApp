package com.musicclubapp.service;

import com.musicclubapp.dto.PresenceResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Obecnosc: kto jest "online" i jak czesto to zapisujemy.
 *
 * <p>Dwie rzeczy warte sprawdzenia i obie latwo popsuc niezauwazenie:
 * granica okna "online" (zbyt krotka - ludzie migaja; zbyt dluga - kropka
 * klamie) oraz ograniczenie czestotliwosci zapisow (bez niego kazde
 * odpytanie czatu to zapis do tabeli {@code users}).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Obecnosc: online i ostatnia aktywnosc")
class PresenceServiceTest {

    @Autowired private PresenceService presence;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        /*
         * Licznik zapisow zyje w beanie wspolnym dla calego kontekstu Springa,
         * a kazdy test dostaje czysta baze. Bez wyczyszczenia jeden test
         * "zablokowalby" zapis w drugim, a wynik zalezalby od kolejnosci
         * ich uruchomienia.
         */
        presence.forgetWriteThrottle();
        userRepository.save(new User("ala", "ala@example.com", "hash"));
        entityManager.flush();
    }

    /** Ustawia date ostatniej aktywnosci Z POMINIECIEM pamieci podrecznej Hibernate'a. */
    private void setLastSeen(LocalDateTime when) {
        entityManager.createNativeQuery(
                "UPDATE users SET last_seen_at = ?1 WHERE username = 'ala'")
            .setParameter(1, when)
            .executeUpdate();
        entityManager.clear();
    }

    private LocalDateTime readLastSeen() {
        entityManager.clear();
        return userRepository.findByUsername("ala").orElseThrow().getLastSeenAt();
    }

    /* ------------------------------------------------------------------ */
    /*  Granica "online"                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("konto, ktore nigdy nic nie robilo, nie jest online")
    void neverSeenIsOffline() {
        PresenceResponse status = presence.of((LocalDateTime) null);

        assertThat(status.online()).isFalse();
        assertThat(status.lastSeenAt()).isNull();
    }

    @Test
    @DisplayName("aktywnosc sprzed chwili to online")
    void recentActivityIsOnline() {
        assertThat(presence.isOnline(LocalDateTime.now().minusSeconds(10))).isTrue();
    }

    @Test
    @DisplayName("aktywnosc sprzed dziesieciu minut to juz NIE online")
    void oldActivityIsOffline() {
        assertThat(presence.isOnline(LocalDateTime.now().minusMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("okno online jest dluzsze niz odstep miedzy zapisami")
    void onlineWindowOutlivesTheWriteInterval() {
        /*
         * To nie jest test na liczbe, tylko na ZALEZNOSC miedzy dwiema
         * liczbami. Gdyby okno bylo krotsze albo rowne odstepowi zapisow,
         * ktos siedzacy przed ekranem migalby miedzy "online" i "offline"
         * w rytmie wlasnego licznika - i nikt by nie wiedzial dlaczego.
         * Ten test pilnuje, zeby zmiana jednej z tych stalych nie przeszla
         * bez zastanowienia nad druga.
         */
        assertThat(PresenceService.ONLINE_MINUTES * 60)
            .isGreaterThan(PresenceService.WRITE_EVERY_SECONDS * 2);
    }

    /* ------------------------------------------------------------------ */
    /*  Zapis daty                                                         */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("pierwsza aktywnosc zapisuje date")
    void firstTouchWritesTheDate() {
        assertThat(readLastSeen()).isNull();

        presence.touch("ala");

        assertThat(readLastSeen()).isNotNull();
        assertThat(presence.of(readLastSeen()).online()).isTrue();
    }

    @Test
    @DisplayName("druga aktywnosc TUZ PO pierwszej nie wraca juz do bazy")
    void repeatedTouchDoesNotWriteAgain() {
        /*
         * Sedno oszczednosci. Otwarte okno czatu odpytuje serwer co kilka
         * sekund - bez tego ograniczenia kazde takie odpytanie bylby zapisem
         * do tabeli users, czyli tej samej, ktora czyta prawie kazde inne
         * zapytanie w aplikacji.
         *
         * Sprawdzamy to tak: po pierwszym zapisie kasujemy date wprost
         * w bazie. Jesli ograniczenie dziala, drugie wywolanie jej NIE
         * przywroci - bo w ogole nie pojdzie do bazy.
         */
        presence.touch("ala");
        setLastSeen(null);

        presence.touch("ala");

        assertThat(readLastSeen()).isNull();
    }

    @Test
    @DisplayName("nieznany login nie wywraca zapisu aktywnosci")
    void unknownUsernameIsHarmless() {
        /*
         * Konto moze zostac skasowane w chwili, gdy jego wlasciciel ma jeszcze
         * otwarta karte. Aktywnosc jest notatka na boku - nie moze przerwac
         * zapytania, ktore i tak zaraz skonczy sie sensownym bledem.
         */
        presence.touch("nie-ma-takiego");
        presence.touch(null);
        presence.touch("  ");
    }
}
