package com.musicclubapp;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

/** Test "smoke" - sprawdza, czy caly kontekst Springa w ogole wstaje. */
@SpringBootTest
@ActiveProfiles("test")
class MusicClubAppApplicationTests {

    @Test
    void applicationContextStarts() {
        // Pusty celowo: samo wstanie kontekstu jest tu sprawdzana rzecza.
    }
}
