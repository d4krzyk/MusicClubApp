package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formulki sprzedazowe i prawne z opisow Ticketmastera. Dwa pierwsze teksty sa przepisane z prawdziwych wydarzen
 * (zrzuty uzytkownika z aplikacji podlaczonej do zywego Ticketmastera) - pod "O wydarzeniu" stalo "pismo kopiuj-wklej"
 * zamiast czegokolwiek o koncercie.
 */
@DisplayName("Ticketmaster - formulki sprzedazowe i prawne poza opisem")
class TicketBoilerplateTest {

    /** Klub Stodola, Eagle-Eye Cherry - cale "O wydarzeniu". */
    static final String STODOLA = "Service fee depends on the ticket prices and the conditions agreed with the Organiser. "
        + "Additional fees may be added to the ticket price if applicable.";

    /** Live Nation - cale "O wydarzeniu", z danymi spolki ("Sp. z o.o." i "No.:" nie moga rozbic zdania). */
    static final String LIVE_NATION = "Service fee - is included in the ticket price. It depends on the ticket prices and the "
        + "conditions agreed with the Organizer. Additional fees may be added to the ticket price if applicable. Seating "
        + "chart reflects general layout of the venue. To buy accessible tickets please contact us. The concert is "
        + "organised by: Live Nation Sp. z o.o., with its registered office in Warsaw, at Wyględowska 10/23, recorded in "
        + "the commercial register maintained by the District Court for the capital city of Warsaw, XIII Division of the "
        + "Commercial Register, under the number 0000216013, EU VAT No.: PL5213300869.";

    @Test
    @DisplayName("prawdziwe opisy z samych formulek - nic nie zostaje (sekcji \"O wydarzeniu\" nie ma)")
    void realBoilerplateOnly() {
        assertThat(TicketBoilerplate.strip(STODOLA)).isNull();
        assertThat(TicketBoilerplate.strip(LIVE_NATION)).isNull();
    }

    @Test
    @DisplayName("prawdziwy opis zostaje, formulki wokol niego znikaja; akapity zostaja")
    void keepsRealDescription() {
        assertThat(TicketBoilerplate.strip(
            "Eagle-Eye Cherry wraca z trasą na 30-lecie albumu Desireless. " + STODOLA))
            .isEqualTo("Eagle-Eye Cherry wraca z trasą na 30-lecie albumu Desireless.");
        assertThat(TicketBoilerplate.strip("Wieczór pełen przebojów!\n\n" + LIVE_NATION + "\n\nSupport: Yot Club."))
            .isEqualTo("Wieczór pełen przebojów!\n\nSupport: Yot Club.");
        String zwykly = "The band will play their debut album in full. Doors open at 7 PM. Expect a few surprises!";
        assertThat(TicketBoilerplate.strip(zwykly)).isEqualTo(zwykly);
        // krotkie slowo na koncu zdania nie skleja go z formulka, ktora po nim stoi
        assertThat(TicketBoilerplate.strip("Sing along with us. Service fee is included in the ticket price."))
            .isEqualTo("Sing along with us.");
    }

    @Test
    @DisplayName("polskie formulki: oplaty, plan sali, bilety dla osob z niepelnosprawnosciami, dane spolki, limity, zwroty")
    void polishBoilerplate() {
        assertThat(TicketBoilerplate.strip("Opłata serwisowa jest wliczona w cenę biletu. Do ceny biletu mogą zostać "
            + "doliczone opłaty dodatkowe. Plan sali ma charakter poglądowy. Organizatorem koncertu jest Live Nation Sp. "
            + "z o.o. z siedzibą w Warszawie, ul. Wyględowska 10/23, KRS 0000216013, NIP 521-330-08-69. Limit biletów: 6 na "
            + "osobę. Bilety nie podlegają zwrotowi. Koncert promuje nową płytę zespołu.")).isEqualTo("Koncert promuje nową płytę zespołu.");
        assertThat(TicketBoilerplate.strip("Bilety dla osób z niepełnosprawnościami do kupienia przez infolinię.")).isNull();
    }

    @Test
    @DisplayName("skrot ani inicjal nie rozcinaja danych spolki - inaczej zostalby kawalek bez znaku rozpoznawczego")
    void abbreviationsDoNotSplit() {
        assertThat(TicketBoilerplate.strip("Gramy cały album. Organizator: Live Nation, ul. Wyględowska 10/23, KRS 0000216013."))
            .isEqualTo("Gramy cały album.");
        assertThat(TicketBoilerplate.strip("Gramy cały album. Organizator: P. Nowak z siedzibą w Gdańsku."))
            .isEqualTo("Gramy cały album.");
        // skrot spoza listy ("lok."): zdanie konczy sie dopiero przed wielka litera, wiec adres sie nie rozpada
        assertThat(TicketBoilerplate.strip("Gramy cały album. Organizator: Agencja ABC, ul. Długa 5 lok. 3, "
            + "00-001 Warszawa, KRS 0000123456.")).isEqualTo("Gramy cały album.");
        assertThat(TicketBoilerplate.zdania("Gra J. Cole. Potem St. Vincent! A na koniec Hey."))
            .containsExactly("Gra J. Cole.", "Potem St. Vincent!", "A na koniec Hey.");
    }

    @Test
    @DisplayName("puste i null bez zmian; samo \"organizuje X\" bez danych spolki to nie formulka")
    void edges() {
        assertThat(TicketBoilerplate.strip(null)).isNull();
        assertThat(TicketBoilerplate.strip("   ")).isNull();
        assertThat(TicketBoilerplate.strip("The concert is organised by Live Nation.")).isEqualTo("The concert is organised by Live Nation.");
        assertThat(TicketBoilerplate.strip("Zespół gra od 2010 roku. Nowa płyta ukazała się w maju."))
            .isEqualTo("Zespół gra od 2010 roku. Nowa płyta ukazała się w maju.");
    }
}
