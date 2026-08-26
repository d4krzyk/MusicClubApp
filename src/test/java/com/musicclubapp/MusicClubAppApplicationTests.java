package com.musicclubapp;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test "smoke" - sprawdza, czy caly kontekst Springa w ogole wstaje.
 *
 * <p>Jesli gdzies zle nazwiesz beana, zapomnisz zaleznosci w pom.xml albo
 * pomylisz sie w application.properties, to ten test padnie jako pierwszy.
 * Warto go odpalac po kazdej wiekszej zmianie.</p>
 *
 * <p>Realizuje czesc wymagania nr 25 ({@code @SpringBootTest}).</p>
 *
 * <p>{@code @ActiveProfiles("test")} wlacza plik
 * {@code application-test.properties}, ktory podmienia PostgreSQL na baze H2
 * w pamieci - dzieki temu test dziala przy wylaczonym Dockerze.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MusicClubAppApplicationTests {

    @Test
    void applicationContextStarts() {
        // Pusty celowo: samo wstanie kontekstu jest tu sprawdzana rzecza.
    }
}
