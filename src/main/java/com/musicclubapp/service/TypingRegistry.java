package com.musicclubapp.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kto do kogo wlasnie pisze - <b>wylacznie w pamieci</b>.
 *
 * <p><b>Dlaczego to NIE idzie do bazy.</b> "Pisze" ma wartosc przez okolo
 * trzy sekundy i potem jest smieciem. Zapisywanie tego do bazy oznaczaloby
 * kilkanascie zapisow na minute na kazda otwarta rozmowe - do tabeli, ktorej
 * cala zawartosc i tak trzeba by kasowac co chwile. To jest podrecznikowy
 * przyklad stanu, ktory ma zyc krocej niz pojedyncze zapytanie do bazy.</p>
 *
 * <p><b>Co tracimy przy restarcie: nic.</b> Wszystkie wpisy i tak wygasaja
 * po {@value #TTL_SECONDS} sekundach. Restart serwera jest wiec nie do
 * odroznienia od zwyklego uplyniecia czasu.</p>
 *
 * <p><b>Ograniczenie, ktore trzeba znac.</b> Mapa zyje w JEDNEJ instancji
 * aplikacji. Gdyby kiedys dzialaly dwie za wspolnym rozdzielaczem ruchu,
 * dymek "pisze" dzialalby tylko wtedy, gdy oboje rozmawiajacy trafia na ten
 * sam serwer. Przy tej aplikacji instancja jest jedna, a lekarstwem bylby
 * wspolny Redis - czyli kolejna usluga do postawienia dla dymka z trzema
 * kropkami. Nie warto.</p>
 */
@Service
public class TypingRegistry {

    /** Jak dlugo od ostatniego sygnalu uznajemy, ze ktos wciaz pisze. */
    public static final int TTL_SECONDS = 5;

    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);

    /**
     * Po przekroczeniu tylu wpisow przegladamy mape i wyrzucamy przeterminowane.
     *
     * <p>Sprzatanie przy KAZDYM sygnale byloby przechodzeniem po calej mapie
     * kilkanascie razy na sekunde. Sprzatanie nigdy - powolnym wyciekiem
     * pamieci przy kazdej rozmowie, ktora sie urwala. Prog jest kompromisem:
     * przy normalnym ruchu nie odpala sie wcale.</p>
     */
    private static final int CLEANUP_THRESHOLD = 500;

    /** "kto→do kogo" → kiedy ostatnio dal znac, ze pisze. */
    private final Map<String, Instant> typing = new ConcurrentHashMap<>();

    /** Odnotowuje, ze {@code sender} pisze do {@code recipient}. */
    public void startedTyping(Long senderId, Long recipientId) {
        if (typing.size() > CLEANUP_THRESHOLD) {
            removeExpired();
        }
        typing.put(key(senderId, recipientId), Instant.now());
    }

    /**
     * Czy {@code sender} pisze wlasnie do {@code recipient}.
     *
     * <p>Sam odczyt <b>usuwa</b> przeterminowany wpis. Dzieki temu mapa
     * sprzata sie przy okazji zwyklego uzytkowania: kazda rozmowa, ktora jest
     * ogladana, czysci po sobie.</p>
     */
    public boolean isTyping(Long senderId, Long recipientId) {
        String key = key(senderId, recipientId);
        Instant since = typing.get(key);

        if (since == null) {
            return false;
        }
        if (since.isBefore(Instant.now().minus(TTL))) {
            typing.remove(key, since);
            return false;
        }
        return true;
    }

    /**
     * Kasuje sygnal - uzywane zaraz po wyslaniu wiadomosci.
     *
     * <p>Bez tego dymek "pisze" wisialby jeszcze kilka sekund POD wlasnie
     * dostarczona wiadomoscia, co wyglada jak usterka.</p>
     */
    public void stoppedTyping(Long senderId, Long recipientId) {
        typing.remove(key(senderId, recipientId));
    }

    /**
     * Kierunek ma znaczenie: to, ze ja pisze do Ciebie, nie znaczy,
     * ze Ty piszesz do mnie. Stad strzalka w kluczu.
     */
    private String key(Long senderId, Long recipientId) {
        return senderId + "->" + recipientId;
    }

    private void removeExpired() {
        Instant deadline = Instant.now().minus(TTL);
        typing.values().removeIf(since -> since.isBefore(deadline));
    }
}
