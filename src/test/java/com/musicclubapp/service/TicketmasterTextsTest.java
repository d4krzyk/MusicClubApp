package com.musicclubapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opisy z Ticketmastera: wydarzenie ma trzy pola tekstu ({@code info}, {@code description}, {@code additionalInfo}),
 * a wykonawca ({@code attraction}) dwa ({@code description}, {@code additionalInfo}) - tak mowi specyfikacja Discovery API.
 * Wczesniej czytalismy z wydarzenia tylko jedno pole, a opisu wykonawcy (sekcja "About" na stronie artysty
 * w ticketmaster.pl) wcale.
 */
@DisplayName("Ticketmaster - opisy wydarzenia i wykonawcow")
class TicketmasterTextsTest {

    private final TicketmasterClient ticketmaster = new TicketmasterClient("k", "http://127.0.0.1:9/x", 1000);
    private final ObjectMapper json = new ObjectMapper();

    private TicketmasterClient.Event wydarzenie(String pola, String wykonawca) throws Exception {
        return ticketmaster.read(json.readTree("""
            {
              "id": "Z1", "name": "Kovacs", "dates": { "start": { "localDate": "2026-11-20", "localTime": "20:00:00" } },
              "_embedded": {
                "venues": [ { "id": "V1", "name": "Progresja", "city": { "name": "Warszawa" } } ],
                "attractions": [ %s ]
              }
              %s
            }
            """.formatted(wykonawca, pola)));
    }

    private static String pole(String nazwa, String tresc) throws Exception {
        return ", \"" + nazwa + "\": " + new ObjectMapper().writeValueAsString(tresc);
    }

    @Test
    @DisplayName("opis wydarzenia ze wszystkich trzech pol, po kolei, bez powtorzen (tekst zawarty w innym odpada)")
    void eventTextFromAllFields() throws Exception {
        var e = wydarzenie(pole("info", "Kovacs wraca do Polski z nową płytą.")
                + pole("description", "Kovacs  wraca do Polski z nową płytą. Support: Yot Club.")
                + pole("additionalInfo", "Bilety kolekcjonerskie dostępne w kasie klubu."),
            "{ \"id\": \"A1\", \"name\": \"Kovacs\" }");
        assertThat(e.description()).isEqualTo(
            "Kovacs wraca do Polski z nową płytą. Support: Yot Club.\n\nBilety kolekcjonerskie dostępne w kasie klubu.");

        // Sam additionalInfo - tez jest opisem (wczesniej przepadal)
        assertThat(wydarzenie(pole("additionalInfo", "Tylko additionalInfo."), "{ \"id\": \"A1\", \"name\": \"K\" }")
            .description()).isEqualTo("Tylko additionalInfo.");
        // Dwa razy to samo (inna wielkosc liter, inne odstepy) - raz
        assertThat(wydarzenie(pole("info", "To samo.") + pole("description", "  to   SAMO. "),
            "{ \"id\": \"A1\", \"name\": \"K\" }").description()).isEqualTo("To samo.");
        assertThat(wydarzenie("", "{ \"id\": \"A1\", \"name\": \"K\" }").description()).isNull();
    }

    @Test
    @DisplayName("formulki sprzedazowe i prawne nie sa opisem: z samych formulek - brak opisu; w uwagach i dostepnosci tez znikaja")
    void boilerplateIsNotDescription() throws Exception {
        var same = wydarzenie(pole("info", TicketBoilerplateTest.LIVE_NATION), "{ \"id\": \"A1\", \"name\": \"K\" }");
        assertThat(same.description()).isNull();

        var mieszane = wydarzenie(pole("info", TicketBoilerplateTest.STODOLA)
                + pole("description", "Eagle-Eye Cherry wraca z trasą na 30-lecie albumu Desireless.")
                + pole("pleaseNote", "Wejście od 18:00. " + TicketBoilerplateTest.STODOLA)
                + ", \"accessibility\": { \"info\": \"Podjazd od ul. Mszczonowskiej. To buy accessible tickets please contact us.\" }",
            "{ \"id\": \"A1\", \"name\": \"Eagle-Eye Cherry\" }");
        assertThat(mieszane.description()).isEqualTo("Eagle-Eye Cherry wraca z trasą na 30-lecie albumu Desireless.");
        assertThat(mieszane.organizer().pleaseNote()).isEqualTo("Wejście od 18:00.");
        assertThat(mieszane.organizer().accessibility()).isEqualTo("Podjazd od ul. Mszczonowskiej.");
    }

    @Test
    @DisplayName("HTML w opisie: akapity i nowe linie zostaja, znaczniki w zdaniu znikaja bez sladu, encje rozkodowane")
    void htmlBecomesPlainText() throws Exception {
        var e = wydarzenie(pole("info",
            "<p>Kovacs to <b>holenderska</b> wokalistka.</p><p>Trasa &amp; nowa płyta<br>w 2026&nbsp;roku</p>"),
            "{ \"id\": \"A1\", \"name\": \"Kovacs\" }");
        assertThat(e.description()).isEqualTo("Kovacs to holenderska wokalistka.\n\nTrasa & nowa płyta\nw 2026 roku");

        // Zwykly tekst z "<" to nie HTML; "&lt;b&gt;" zostaje napisem "<b>"
        assertThat(PlainText.of("Kocham was <3 i &lt;b&gt; zostaje")).isEqualTo("Kocham was <3 i <b> zostaje");
        assertThat(PlainText.of("  linia   1  \n\n\n\n  linia 2 ")).isEqualTo("linia 1\n\nlinia 2");
        assertThat(PlainText.of("<p> </p><br>")).isNull();
        // znacznik w zdaniu przy kropce albo w srodku slowa - bez sladu (spacja dawala "Warszawie ." i "Zesp o l")
        assertThat(PlainText.of("Zesp<i>o</i>ł gra w <b>Warszawie</b>.")).isEqualTo("Zespoł gra w Warszawie.");
        // pojedynczy znacznik akapitu (bez sasiedniego </p>) tez daje pusta linie, a nie zwykle przejscie do nowej
        assertThat(PlainText.of("Pierwszy akapit.<p>Drugi akapit.")).isEqualTo("Pierwszy akapit.\n\nDrugi akapit.");
        assertThat(PlainText.of("<script>alert(1)</script>Tekst")).isEqualTo("Tekst");
    }

    @Test
    @DisplayName("opis wykonawcy: description + additionalInfo bez powtorzen, jezyk z locale, strona artysty tylko https/http")
    void performerAbout() throws Exception {
        var e = wydarzenie("", """
            { "id": "K8vZ917G1W0", "name": "Kovacs", "locale": "en-us",
              "url": "https://www.ticketmaster.pl/artist/kovacs-tickets/950040",
              "description": "<p>Sharon Kovacs is a Dutch singer-songwriter.</p>",
              "additionalInfo": "Sharon Kovacs is a Dutch singer-songwriter. Her debut album Shades of Black came out in 2015." },
            { "id": "A2", "name": "Support", "locale": "pl-pl", "url": "javascript:alert(1)",
              "additionalInfo": "Duet z Gdańska." },
            { "id": "A3", "name": "Bez opisu", "url": "https://www.ticketmaster.pl/artist/x" }
            """);
        var kovacs = e.performers().get(0).about();
        assertThat(kovacs.text()).isEqualTo(
            "Sharon Kovacs is a Dutch singer-songwriter. Her debut album Shades of Black came out in 2015.");
        assertThat(kovacs.lang()).isEqualTo("en");
        assertThat(kovacs.url()).isEqualTo("https://www.ticketmaster.pl/artist/kovacs-tickets/950040");

        var support = e.performers().get(1).about();
        assertThat(support.text()).isEqualTo("Duet z Gdańska.");
        assertThat(support.lang()).isEqualTo("pl");
        assertThat(support.url()).as("niebezpieczny adres").isNull();

        assertThat(e.performers().get(2).about()).as("bez tekstu nie ma opisu").isNull();
    }

    @Test
    @DisplayName("za dlugi opis wykonawcy uciety na granicy slowa")
    void longAboutIsCut() throws Exception {
        String dlugi = "slowo ".repeat(600);
        var about = wydarzenie("", "{ \"id\": \"A1\", \"name\": \"K\", \"description\": \"" + dlugi + "\" }")
            .performers().get(0).about();
        assertThat(about.text().length()).isLessThanOrEqualTo(TicketmasterClient.MAX_O_WYKONAWCY);
        assertThat(about.text()).endsWith("slowo…");
    }
}
