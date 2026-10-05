package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.PerformerLinkKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dane od organizatora i linki wykonawcow z odpowiedzi Ticketmastera (pola wg dokumentacji Discovery API - z tego
 * srodowiska serwis jest zablokowany, wiec ksztalt jest odtworzony z dokumentacji, nie z zywej odpowiedzi).
 */
@DisplayName("Ticketmaster - od organizatora i linki wykonawcow")
class TicketmasterOrganizerTest {

    private final TicketmasterClient ticketmaster = new TicketmasterClient("k", "http://127.0.0.1:9/x", 1000);
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode wydarzenie(String dodatki, String linki) throws Exception {
        return json.readTree("""
            {
              "id": "Z1", "name": "Koncert", "dates": { "start": { "localDate": "2026-11-20", "localTime": "20:00:00" } },
              "_embedded": {
                "venues": [ { "id": "V1", "name": "Progresja", "city": { "name": "Warszawa" } } ],
                "attractions": [ { "id": "A1", "name": "Zespol", "externalLinks": %s } ]
              }
              %s
            }
            """.formatted(linki, dodatki));
    }

    @Test
    @DisplayName("uwagi, organizator, ceny (standardowe przed VIP), wiek, start sprzedazy, dostepnosc")
    void organizer() throws Exception {
        var e = ticketmaster.read(wydarzenie("""
            , "pleaseNote": "  Wejscie od 18:00. Zakaz wnoszenia aparatow.  ",
              "promoter": { "id": "P1", "name": "Live Nation Polska" },
              "priceRanges": [ { "type": "vip", "currency": "pln", "min": 499.0, "max": 699.0 },
                               { "type": "standard", "currency": "pln", "min": 129.0, "max": 189.5 } ],
              "ageRestrictions": { "legalAgeEnforced": true },
              "sales": { "public": { "startDateTime": "2026-10-10T08:00:00Z", "startTBD": false } },
              "accessibility": { "info": "Podjazd dla wozkow od ul. Mszczonowskiej." }
            """, "{}")).organizer();
        assertThat(e.pleaseNote()).isEqualTo("Wejscie od 18:00. Zakaz wnoszenia aparatow.");
        assertThat(e.promoter()).isEqualTo("Live Nation Polska");
        assertThat(e.priceMin()).isEqualTo(129.0);
        assertThat(e.priceMax()).isEqualTo(189.5);
        assertThat(e.priceCurrency()).isEqualTo("PLN");
        assertThat(e.ageRestricted()).isTrue();
        assertThat(e.salesStart()).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(e.accessibility()).isEqualTo("Podjazd dla wozkow od ul. Mszczonowskiej.");
    }

    @Test
    @DisplayName("bez tych pol: nic; organizator z listy promoters; zla cena i waluta odrzucone; sprzedaz 'do ustalenia' pusta")
    void missingAndOdd() throws Exception {
        var pusto = ticketmaster.read(wydarzenie("", "{}")).organizer();
        assertThat(pusto).isEqualTo(TicketmasterClient.Organizer.NONE);

        var dziwne = ticketmaster.read(wydarzenie("""
            , "promoters": [ { "name": "Agencja X" } ],
              "priceRanges": [ { "type": "standard", "currency": "zl.", "min": 150, "max": 90 } ],
              "sales": { "public": { "startDateTime": "2026-10-10T08:00:00Z", "startTBD": true } }
            """, "{}")).organizer();
        assertThat(dziwne.promoter()).isEqualTo("Agencja X");
        assertThat(dziwne.priceMin()).isEqualTo(150.0);
        assertThat(dziwne.priceMax()).as("max mniejsze od min").isNull();
        assertThat(dziwne.priceCurrency()).as("waluta spoza trzech liter").isNull();
        assertThat(dziwne.salesStart()).isNull();

        var bezCen = ticketmaster.read(wydarzenie("""
            , "priceRanges": [ { "type": "standard", "currency": "PLN", "min": -5 } ],
              "sales": { "public": { "startDateTime": "jutro" } }
            """, "{}")).organizer();
        assertThat(bezCen.priceMin()).isNull();
        assertThat(bezCen.priceCurrency()).as("bez cen nie ma waluty").isNull();
        assertThat(bezCen.salesStart()).isNull();
    }

    @Test
    @DisplayName("linki wykonawcy: znane rodzaje, pierwszy adres, tylko http(s) z hostem, bez loginu w adresie, do 500 znakow")
    void performerLinks() throws Exception {
        var e = ticketmaster.read(wydarzenie("", """
            {
              "spotify": [ { "url": "https://open.spotify.com/artist/abc" }, { "url": "https://open.spotify.com/artist/zly" } ],
              "homepage": [ { "url": "https://zespol.pl/" } ],
              "instagram": [ { "url": "javascript:alert(1)" } ],
              "facebook": [ { "url": "https://user:haslo@facebook.com/zespol" } ],
              "youtube": [ { "url": "https://www.youtube.com/@zespol x" } ],
              "wiki": [ { "url": "https://pl.wikipedia.org/wiki/%s" } ],
              "twitter": [ { "url": "https://twitter.com/zespol" } ],
              "musicbrainz": [ { "id": "1234" } ]
            }
            """.formatted("a".repeat(480))));
        Map<PerformerLinkKind, String> linki = e.performers().get(0).links();
        assertThat(linki).containsOnlyKeys(PerformerLinkKind.SPOTIFY, PerformerLinkKind.HOMEPAGE);
        assertThat(linki.get(PerformerLinkKind.SPOTIFY)).isEqualTo("https://open.spotify.com/artist/abc");

        assertThat(TicketmasterClient.safeLink("https://pl.wikipedia.org/wiki/Zespol")).isTrue();
        assertThat(TicketmasterClient.safeLink("ftp://zespol.pl/")).isFalse();
        assertThat(TicketmasterClient.safeLink("https:///bez-hosta")).isFalse();
        assertThat(ticketmaster.read(wydarzenie("", "{}")).performers().get(0).links()).isEmpty();
    }
}
