package com.musicclubapp.service;

import com.musicclubapp.error.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ile wiadomosci z linkiem moze zamowic jeden adres sieciowy na godzine.
 *
 * <p>Limit na konto (patrz EmailVerificationService) nie wystarcza: kazda
 * rejestracja to nowe konto, wiec ktos moglby zakladac konta na cudze adresy
 * i uzywac naszego serwera do zasypywania ich poczta. Tu liczymy wszystko,
 * co przyszlo z jednego adresu IP - w pamieci, bo serwer jest jeden.</p>
 */
@Component
public class MailRateLimiter {

    private static final Duration OKNO = Duration.ofHours(1);

    private final Clock clock;
    private final int naGodzine;
    private final Map<String, Deque<Long>> wysylki = new ConcurrentHashMap<>();

    public MailRateLimiter(Clock clock, @Value("${app.mail.per-ip-per-hour:20}") int naGodzine) {
        this.clock = clock;
        this.naGodzine = naGodzine;
    }

    /** Zapisuje wysylke z tego adresu - albo rzuca 429, gdy limit juz wyczerpany. */
    public void acquire(String ip) {
        long teraz = clock.millis();
        Deque<Long> kolejka = wysylki.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (kolejka) {
            usunStare(kolejka, teraz);
            if (kolejka.size() >= naGodzine) {
                long czekaj = (kolejka.peekFirst() + OKNO.toMillis() - teraz + 999) / 1000;
                throw new TooManyRequestsException(czekaj);
            }
            kolejka.addLast(teraz);
        }
    }

    /** Zapomina adresy, z ktorych od godziny nic nie przyszlo - zeby mapa nie rosla bez konca. */
    public void forgetOld() {
        long teraz = clock.millis();
        wysylki.entrySet().removeIf(wpis -> {
            synchronized (wpis.getValue()) {
                usunStare(wpis.getValue(), teraz);
                return wpis.getValue().isEmpty();
            }
        });
    }

    private static void usunStare(Deque<Long> kolejka, long teraz) {
        while (!kolejka.isEmpty() && kolejka.peekFirst() <= teraz - OKNO.toMillis()) {
            kolejka.pollFirst();
        }
    }
}
