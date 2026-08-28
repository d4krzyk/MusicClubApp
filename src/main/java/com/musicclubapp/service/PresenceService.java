package com.musicclubapp.service;

import com.musicclubapp.dto.PresenceResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kto jest teraz aktywny i kiedy byl ostatnio - <b>jedno miejsce na cala regule</b>.
 *
 * <p><b>Czym tak naprawde jest "online".</b> Serwer nie ma jak sie dowiedziec,
 * ze ktos zamknal karte: przegladarka tego nie melduje, a HTTP nie utrzymuje
 * polaczenia. Jedyne, co wiemy, to <i>kiedy przyszlo od kogos ostatnie
 * zapytanie</i>. Dlatego "online" znaczy tu dokladnie tyle: <b>cos robil
 * w ciagu ostatnich {@value #ONLINE_MINUTES} minut</b>. Nie udajemy, ze to
 * cos wiecej - i dlatego obok kropki zawsze pokazujemy takze date, zeby
 * uzytkownik mogl sam ocenic, na ile jest swieza.</p>
 *
 * <p><b>Dlaczego nie zapisujemy daty przy KAZDYM zapytaniu.</b> Otwarte okno
 * czatu odpytuje serwer co kilka sekund. Zapis przy kazdym zapytaniu
 * oznaczalby kilkanascie zapisow do bazy na minute na kazda otwarta karte -
 * i to zapisow do tabeli {@code users}, czyli tej samej, ktora czyta prawie
 * kazde inne zapytanie. Dlatego zapisujemy najwyzej raz na
 * {@value #WRITE_EVERY_SECONDS} sekund, a o tym, czy juz czas, decyduje
 * licznik w pamieci.</p>
 *
 * <p><b>Odstepy sa dobrane, a nie przypadkowe.</b> Okno "online" musi byc
 * WYRAZNIE dluzsze od odstepu miedzy zapisami - inaczej ktos siedzacy przed
 * ekranem migalby miedzy "online" i "offline" w rytmie wlasnego licznika.
 * Trzy minuty przy zapisie co 45 sekund daja cztery szanse na trafienie.</p>
 *
 * <p><b>Licznik w pamieci znika przy restarcie</b> - i nic sie nie dzieje.
 * Po restarcie pierwsze zapytanie kazdej osoby po prostu zapisze date od nowa.
 * Trzymanie go w bazie byloby zapisywaniem po to, zeby unikac zapisywania.</p>
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /** Ile minut od ostatniej aktywnosci uznajemy jeszcze za "online". */
    public static final int ONLINE_MINUTES = 3;

    /** Jak czesto najwyzej zapisujemy date do bazy. */
    public static final int WRITE_EVERY_SECONDS = 45;

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(ONLINE_MINUTES);
    private static final Duration WRITE_EVERY = Duration.ofSeconds(WRITE_EVERY_SECONDS);

    /**
     * Zabezpieczenie przed rozrostem mapy.
     *
     * <p>Kluczem jest login ZALOGOWANEJ osoby, wiec mapa nie moze urosnac
     * ponad liczbe kont - przy tej aplikacji to nie jest zagrozenie.
     * Ograniczenie jest tu na wypadek, gdyby kiedys trafialy tu takze konta
     * usuwane i zakladane w kolko: po przekroczeniu progu czyscimy calosc,
     * co kosztuje tylko jedna dodatkowa runde zapisow.</p>
     */
    private static final int MAX_TRACKED = 10_000;

    /** Login → kiedy ostatnio zapisalismy jego date do bazy. */
    private final Map<String, LocalDateTime> lastWrite = new ConcurrentHashMap<>();

    private final UserRepository userRepository;

    public PresenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Odnotowuje, ze ta osoba wlasnie cos zrobila.
     *
     * <p>Wywolywane przy kazdym zapytaniu zalogowanego uzytkownika
     * ({@code PresenceInterceptor}), wiec musi byc tanie - i jest: w typowym
     * przypadku konczy sie porownaniem dwoch dat w pamieci.</p>
     *
     * <p><b>Zadny blad tutaj nie moze przerwac zapytania.</b> Data ostatniej
     * aktywnosci jest ozdoba; gdyby jej zapis mial wywrocic wysylanie
     * wiadomosci, byloby to zamienienie drobiazgu na awarie.</p>
     */
    @Transactional
    public void touch(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime written = lastWrite.get(username);

        if (written != null && written.isAfter(now.minus(WRITE_EVERY))) {
            return;   // zapisane niedawno - nie ma po co wracac do bazy
        }

        try {
            if (lastWrite.size() > MAX_TRACKED) {
                lastWrite.clear();
            }
            /*
             * Zapis jednym UPDATE, bez wczytywania encji. Wczytanie uzytkownika
             * tylko po to, zeby ustawic jedna kolumne, oznaczaloby dodatkowy
             * SELECT przy kazdym odswiezeniu - a tu chodzi wlasnie o to,
             * zeby bylo tanio.
             */
            userRepository.touchLastSeen(username, now);
            lastWrite.put(username, now);
        } catch (Exception e) {
            log.debug("Nie udalo sie zapisac aktywnosci uzytkownika {}: {}",
                username, e.getMessage());
        }
    }

    /** Obecnosc danej osoby - do wstawienia w DTO profilu, kafelka czy rozmowy. */
    public PresenceResponse of(User user) {
        return of(user == null ? null : user.getLastSeenAt());
    }

    /**
     * Obecnosc wyliczona z samej daty.
     *
     * <p>Wersja dla zapytan, ktore oddaja date w projekcji, a nie cala encje
     * (lista znajomych). <b>Regula pozostaje jedna</b> - gdyby kazde z tych
     * miejsc porownywalo daty samo, wystarczyloby raz wpisac inna liczbe
     * minut, zeby ta sama osoba byla "online" na jednym ekranie i "offline"
     * na drugim.</p>
     */
    public PresenceResponse of(LocalDateTime lastSeenAt) {
        return new PresenceResponse(isOnline(lastSeenAt), lastSeenAt);
    }

    /** Czy data ostatniej aktywnosci miesci sie jeszcze w oknie "online". */
    public boolean isOnline(LocalDateTime lastSeenAt) {
        return lastSeenAt != null
            && lastSeenAt.isAfter(LocalDateTime.now().minus(ONLINE_WINDOW));
    }

    /**
     * Czysci licznik zapisow - <b>wylacznie na potrzeby testow</b>.
     *
     * <p>Kazdy test startuje na wlasnej bazie, ale ta mapa zyje w beanie
     * wspolnym dla calego kontekstu Springa. Bez tego jeden test "zablokowalby"
     * zapis w drugim, a wynik zalezalby od kolejnosci ich uruchomienia -
     * czyli od czegos, czego nikt nie kontroluje.</p>
     */
    public void forgetWriteThrottle() {
        lastWrite.clear();
    }
}
