package com.musicclubapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test "smoke" - sprawdza, czy caly kontekst Springa w ogole wstaje.
 *
 * <p>Jesli gdzies zle nazwiesz beana, zapomnisz zaleznosci w pom.xml albo
 * pomylisz sie w application.properties, to ten test padnie jako pierwszy.
 * Warto go odpalac po kazdej wiekszej zmianie.</p>
 *
 * <p>Realizuje czesc wymagania nr 25 ({@code @SpringBootTest}).</p>
 */
@SpringBootTest
class MusicClubAppApplicationTests {

    @Test
    void kontekstAplikacjiSieUruchamia() {
        // Pusty celowo: samo wstanie kontekstu jest tu sprawdzana rzecza.
    }
}
