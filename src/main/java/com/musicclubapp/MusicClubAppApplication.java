package com.musicclubapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punkt wejscia aplikacji.
 * <p>
 * Adnotacja {@code @SpringBootApplication} wlacza trzy rzeczy naraz:
 * <ul>
 *   <li>{@code @Configuration} - ta klasa moze definiowac beany,</li>
 *   <li>{@code @EnableAutoConfiguration} - Spring sam konfiguruje to, co znajdzie
 *       na classpath (np. widzi sterownik PostgreSQL -&gt; tworzy DataSource),</li>
 *   <li>{@code @ComponentScan} - skanuje pakiet {@code com.musicclubapp} i nizej
 *       w poszukiwaniu klas z {@code @Service}, {@code @RestController} itd.</li>
 * </ul>
 * Dlatego wszystkie nasze klasy MUSZA lezec w podpakietach {@code com.musicclubapp}.
 */
@SpringBootApplication
public class MusicClubAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicClubAppApplication.class, args);
    }
}
