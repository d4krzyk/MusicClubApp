package com.musicclubapp.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Lista miast i odleglosci - bez Springa, na samym pliku z miastami. */
@DisplayName("Miasta i odleglosci")
class CityIndexTest {

    private static final CityIndex INDEX = new CityIndex();

    @BeforeAll
    static void load() {
        INDEX.load();
    }

    private static double km(String a, String b) {
        CityIndex.City x = INDEX.find(a).orElseThrow();
        CityIndex.City y = INDEX.find(b).orElseThrow();
        return CityIndex.distanceKm(x.latitude(), x.longitude(), y.latitude(), y.longitude());
    }

    @Test
    @DisplayName("miasto rozpoznajemy po nazwie z polskimi znakami, bez nich, wielkimi literami i po angielsku")
    void findsCityByAnyWriting() {
        assertThat(INDEX.find("Poznań").orElseThrow().name()).isEqualTo("Poznań");
        assertThat(INDEX.find("poznan").orElseThrow().name()).isEqualTo("Poznań");
        assertThat(INDEX.find("  POZNAN ").orElseThrow().name()).isEqualTo("Poznań");
        assertThat(INDEX.find("Łódź").orElseThrow().name()).isEqualTo("Łódź");
        assertThat(INDEX.find("lodz").orElseThrow().name()).isEqualTo("Łódź");
        // Ticketmaster pisze "Warsaw", a import robi z "Warszawa" ten sam klucz
        assertThat(INDEX.find("Warsaw").orElseThrow().name()).isEqualTo("Warszawa");
        assertThat(INDEX.find("Warszawa").orElseThrow().name()).isEqualTo("Warszawa");
        assertThat(INDEX.byKey(EventImportService.cityKey("Warszawa")).orElseThrow().name()).isEqualTo("Warszawa");
        assertThat(INDEX.find("Cracow").orElseThrow().name()).isEqualTo("Kraków");
        assertThat(INDEX.find("Zalesie Dolne")).isEmpty();
        assertThat(INDEX.find("  ")).isEmpty();
        assertThat(INDEX.find(null)).isEmpty();
    }

    @Test
    @DisplayName("odleglosci miedzy znanymi miastami zgadzaja sie z mapa")
    void knownDistances() {
        assertThat(km("Warszawa", "Kraków")).isCloseTo(252, within(8.0));
        assertThat(km("Warszawa", "Gdańsk")).isCloseTo(284, within(10.0));
        assertThat(km("Warszawa", "Wrocław")).isCloseTo(301, within(10.0));
        assertThat(km("Warszawa", "Poznań")).isCloseTo(279, within(10.0));
        // w linii prostej, nie drogą (po szosie to ok. 180 km)
        assertThat(km("Poznań", "Wrocław")).isCloseTo(145, within(6.0));
        assertThat(km("Kraków", "Katowice")).isCloseTo(68, within(6.0));
        assertThat(km("Gdańsk", "Gdynia")).isCloseTo(20, within(6.0));
        assertThat(km("Poznań", "Swarzędz")).isCloseTo(11, within(5.0));
        assertThat(km("Poznań", "Poznań")).isZero();
    }

    @Test
    @DisplayName("kazde miasto lezy w Polsce, a klucze sa unikalne")
    void everyCityIsInPoland() {
        Set<String> keys = new HashSet<>();
        for (CityIndex.City c : INDEX.search("", 1000)) {
            keys.add(c.key());
        }
        // pusty tekst nic nie podpowiada - lista wszystkich przez szukanie po kazdej literze
        List<CityIndex.City> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (char litera = 'a'; litera <= 'z'; litera++) {
            for (CityIndex.City c : INDEX.search(String.valueOf(litera), 1000)) {
                if (seen.add(c.key())) {
                    all.add(c);
                }
            }
        }
        assertThat(all.size()).isGreaterThan(200);
        for (CityIndex.City c : all) {
            assertThat(c.latitude()).as(c.name()).isBetween(49.0, 54.9);
            assertThat(c.longitude()).as(c.name()).isBetween(14.1, 24.2);
        }
    }

    @Test
    @DisplayName("literowka we wspolrzednych wyszlaby jako miasto bez sasiadow w poblizu")
    void everyCityHasANeighbourNearby() {
        List<CityIndex.City> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (char litera = 'a'; litera <= 'z'; litera++) {
            for (CityIndex.City c : INDEX.search(String.valueOf(litera), 1000)) {
                if (seen.add(c.key())) {
                    all.add(c);
                }
            }
        }
        for (CityIndex.City c : all) {
            double nearest = all.stream().filter(o -> o != c)
                .mapToDouble(o -> CityIndex.distanceKm(c.latitude(), c.longitude(), o.latitude(), o.longitude()))
                .min().orElseThrow();
            assertThat(nearest).as("najblizszy sasiad miasta " + c.name()).isLessThan(60);
        }
    }

    @Test
    @DisplayName("podpowiedzi: najpierw nazwy zaczynajace sie od wpisanego tekstu, od najwiekszych miast")
    void hints() {
        assertThat(INDEX.search("poz", 5)).extracting(CityIndex.City::name).containsExactly("Poznań");
        assertThat(INDEX.search("GD", 2)).extracting(CityIndex.City::name).containsExactly("Gdańsk", "Gdynia");
        // "Starogard Gdański" ma "gd" w środku, więc jest dopiero po tych, które od tego zaczynają
        assertThat(INDEX.search("GD", 3)).extracting(CityIndex.City::name).containsExactly("Gdańsk", "Gdynia", "Starogard Gdański");
        assertThat(INDEX.search("war", 3)).extracting(CityIndex.City::name).startsWith("Warszawa");
        // "ow" jest w srodku nazw - zaczynajacych sie od "ow" nie ma, wiec wychodza te, w ktorych jest w srodku
        assertThat(INDEX.search("ow", 3)).hasSize(3);
        assertThat(INDEX.search("", 5)).isEmpty();
        assertThat(INDEX.search("xyzxyz", 5)).isEmpty();
        assertThat(INDEX.search("a", 2)).hasSize(2);
    }
}
