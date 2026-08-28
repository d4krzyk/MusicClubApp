package com.musicclubapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneOffset;
import java.util.TimeZone;

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
        /*
         * Cala aplikacja liczy czas w UTC - niezaleznie od tego, gdzie stoi
         * serwer i jak ma ustawiony zegar.
         *
         * Blad, ktory to wymusil: uzytkownik aktywny PRZED CHWILA byl
         * pokazywany jako "aktywny 2 godziny temu", a wiadomosc wyslana
         * przed sekunda dostawala godzine sprzed dwoch godzin.
         *
         * Skad to sie bralo. W bazie trzymamy LocalDateTime, czyli date
         * BEZ strefy - i taka tez wychodzila do przegladarki:
         * "2026-08-28T12:00:00". Serwer zapisywal ja w UTC, ale w tekscie
         * nie ma o tym ani slowa, wiec przegladarka - zgodnie z norma
         * jezyka JavaScript - czytala ja jako czas LOKALNY. W Polsce
         * latem daje to blad dokladnie dwoch godzin.
         *
         * Naprawa sklada sie z dwoch polowek i obie sa konieczne:
         *   1. tutaj  - przypinamy zegar serwera do UTC, zeby zapisywana
         *               data zawsze znaczyla to samo, takze wtedy, gdy
         *               aplikacja chodzi na laptopie ustawionym na Warszawe;
         *   2. w JacksonConfig - dopisujemy do wysylanej daty "Z", czyli
         *               mowimy wprost: to jest UTC.
         *
         * Sama druga polowka byla by klamstwem (dopisywalibysmy "Z" do
         * czasu warszawskiego), a sama pierwsza niczego by nie zmienila,
         * bo przegladarka dalej nie wiedzialaby, co czyta.
         *
         * Ustawiamy to PRZED startem Springa - pozniej byloby za pozno
         * dla polaczenia z baza i dla Jacksona, ktore zapamietuja strefe
         * przy tworzeniu.
         */
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));

        SpringApplication.run(MusicClubAppApplication.class, args);
    }
}
