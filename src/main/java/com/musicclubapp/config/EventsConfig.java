package com.musicclubapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Zadania w tle i zegar.
 *
 * @EnableScheduling wlacza adnotacje @Scheduled - bez tego import wydarzen
 * z Ticketmastera bylby zwykla metoda, ktorej nikt nigdy nie wola.
 */
@Configuration
@EnableScheduling
public class EventsConfig {

    /**
     * Zegar jako osobny obiekt, a nie wolanie "teraz" w srodku kodu.
     *
     * Testy podstawiaja zegar zatrzymany w jednym dniu. Bez tego test
     * z koncertem "za tydzien" zaczalby sie sypac, gdy ten tydzien minie.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
