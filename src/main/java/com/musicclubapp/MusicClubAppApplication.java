package com.musicclubapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneOffset;
import java.util.TimeZone;

/** Punkt wejscia aplikacji. */
@SpringBootApplication
public class MusicClubAppApplication {

    public static void main(String[] args) {
        /*
         * Cala aplikacja liczy czas w UTC - niezaleznie od tego, gdzie stoi serwer i jak ma
         * ustawiony zegar.
         */
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));

        SpringApplication.run(MusicClubAppApplication.class, args);
    }
}
