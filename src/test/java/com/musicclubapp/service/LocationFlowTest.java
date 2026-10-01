package com.musicclubapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.EventPerformer;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Miasto w profilu i to, co z niego wynika: podpowiedzi miast, propozycje znajomych, wydarzenia
 * i klany z okolicy - przez prawdziwe API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Lokalizacja - miasto w profilu i okolica")
class LocationFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ArtistRepository artists;
    @Autowired private MusicEventRepository events;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        for (String login : List.of("lo_ja", "lo_poznan", "lo_swarzedz", "lo_gniezno", "lo_krakow", "lo_bez")) {
            users.save(new User(login, login + "@example.com", "x"));
        }
        em.flush();
    }

    /* ---------------------------- pomocnicze ---------------------------- */

    private ResultActions get_(String kto, String adres) throws Exception {
        return mvc.perform(get(adres).with(user(kto)).header("Accept-Language", "pl"));
    }

    private ResultActions put_(String kto, String adres, Object tresc) throws Exception {
        return mvc.perform(put(adres).with(user(kto)).with(csrf()).header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc)));
    }

    private ResultActions post_(String kto, String adres, Object tresc) throws Exception {
        return mvc.perform(post(adres).with(user(kto)).with(csrf()).header("Accept-Language", "pl")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tresc)));
    }

    private JsonNode tresc(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode miasto(String kto, String tekst) throws Exception {
        JsonNode odpowiedz = tresc(put_(kto, "/api/profile/location", Map.of("city", tekst)).andExpect(status().isOk()));
        em.flush();
        return odpowiedz;
    }

    private static List<String> teksty(JsonNode tablica, String pole) {
        List<String> wynik = new ArrayList<>();
        tablica.forEach(w -> wynik.add(w.get(pole).asText()));
        return wynik;
    }

    /* ---------------------------- profil ---------------------------- */

    @Test
    @DisplayName("miasto z listy dostaje nazwe z polskimi znakami i wspolrzedne, nieznane zostaje tekstem")
    void setsCity() throws Exception {
        JsonNode znane = miasto("lo_ja", "  poznan ");
        assertThat(znane.get("city").asText()).isEqualTo("Poznań");
        assertThat(znane.get("cityLocated").asBoolean()).isTrue();
        User ja = users.findByUsername("lo_ja").orElseThrow();
        assertThat(ja.getCityKey()).isEqualTo("poznan");
        assertThat(ja.getCityLatitude()).isBetween(52.3, 52.5);
        assertThat(ja.getCityLongitude()).isBetween(16.8, 17.0);

        JsonNode nieznane = miasto("lo_ja", "Zalesie   Dolne");
        assertThat(nieznane.get("city").asText()).isEqualTo("Zalesie Dolne");
        assertThat(nieznane.get("cityLocated").asBoolean()).isFalse();
        assertThat(users.findByUsername("lo_ja").orElseThrow().getCityLatitude()).isNull();

        JsonNode pusty = miasto("lo_ja", "");
        assertThat(pusty.get("city").isNull()).isTrue();
        assertThat(users.findByUsername("lo_ja").orElseThrow().getCityKey()).isNull();
    }

    @Test
    @DisplayName("zle miasto: za dlugie albo ze znakami spoza liter - 422, nic sie nie zapisuje")
    void rejectsBadCity() throws Exception {
        miasto("lo_ja", "Gdańsk");
        put_("lo_ja", "/api/profile/location", Map.of("city", "x".repeat(61))).andExpect(status().isUnprocessableEntity());
        put_("lo_ja", "/api/profile/location", Map.of("city", "Poz<b>nań")).andExpect(status().isUnprocessableEntity());
        put_("lo_ja", "/api/profile/location", Map.of("city", "12345")).andExpect(status().isUnprocessableEntity());
        em.clear();
        assertThat(users.findByUsername("lo_ja").orElseThrow().getCity()).isEqualTo("Gdańsk");
    }

    @Test
    @DisplayName("podpowiedzi miast: po prefiksie, bez polskich znakow; tylko dla zalogowanych")
    void cityHints() throws Exception {
        JsonNode podpowiedzi = tresc(get_("lo_ja", "/api/cities?q=poz").andExpect(status().isOk()));
        assertThat(teksty(podpowiedzi, "name")).containsExactly("Poznań");
        assertThat(teksty(tresc(get_("lo_ja", "/api/cities?q=gd")), "name")).startsWith("Gdańsk", "Gdynia");
        assertThat(tresc(get_("lo_ja", "/api/cities?q=")).size()).isZero();
        mvc.perform(get("/api/cities?q=poz")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("miasto na cudzym profilu: widac je przy pelnym profilu, chyba ze ktos je ukryl; swoje zawsze")
    void cityOnProfile() throws Exception {
        miasto("lo_poznan", "Poznań");
        assertThat(tresc(get_("lo_ja", "/api/profiles/lo_poznan")).get("city").asText()).isEqualTo("Poznań");

        User poznan = users.findByUsername("lo_poznan").orElseThrow();
        poznan.setPrivacy(ProfileVisibility.EVERYONE, poznan.getFriendRequestsFrom(), poznan.getClanInvitesFrom(),
            true, true, false, false);
        em.flush();
        assertThat(tresc(get_("lo_ja", "/api/profiles/lo_poznan")).get("city").isNull()).isTrue();
        assertThat(tresc(get_("lo_poznan", "/api/profiles/lo_poznan")).get("city").asText()).isEqualTo("Poznań");

        // profil tylko dla znajomych - obcy nie dostaje miasta, choc ustawienie "pokazuj" jest wlaczone
        poznan.setPrivacy(ProfileVisibility.FRIENDS, poznan.getFriendRequestsFrom(), poznan.getClanInvitesFrom(),
            true, true, false, true);
        em.flush();
        assertThat(tresc(get_("lo_ja", "/api/profiles/lo_poznan")).get("city").isNull()).isTrue();
    }

    /* ------------------------ propozycje znajomych ------------------------ */

    private JsonNode propozycje(String kto) throws Exception {
        return tresc(get_(kto, "/api/friends/suggestions?limit=60").andExpect(status().isOk()));
    }

    /** Loginy z tego testu, w kolejnosci listy - w bazie moze byc jeszcze konto administratora. */
    private static List<String> lokalni(JsonNode lista) {
        return teksty(lista, "username").stream().filter(l -> l.startsWith("lo_")).toList();
    }

    private static JsonNode osoba(JsonNode lista, String login) {
        for (JsonNode o : lista) {
            if (login.equals(o.get("username").asText())) {
                return o;
            }
        }
        throw new AssertionError("brak " + login + " na liscie: " + lista);
    }

    @Test
    @DisplayName("propozycje: blizsze miasto wyzej, daleko i bez miasta na koncu; podpis bez odleglosci")
    void suggestionsPreferNearby() throws Exception {
        miasto("lo_ja", "Poznań");
        miasto("lo_poznan", "Poznań");
        miasto("lo_swarzedz", "Swarzędz");
        miasto("lo_gniezno", "Gniezno");
        miasto("lo_krakow", "Kraków");

        JsonNode lista = propozycje("lo_ja");
        List<String> loginy = lokalni(lista);

        assertThat(loginy.subList(0, 3)).containsExactly("lo_poznan", "lo_swarzedz", "lo_gniezno");
        // bez miasta i z Krakowa (poza zasiegiem) - po loginie
        assertThat(loginy.subList(3, 5)).containsExactly("lo_bez", "lo_krakow");

        assertThat(osoba(lista, "lo_poznan").get("proximity").asText()).isEqualTo("SAME_CITY");
        assertThat(osoba(lista, "lo_swarzedz").get("proximity").asText()).isEqualTo("NEARBY");
        assertThat(osoba(lista, "lo_gniezno").get("proximity").asText()).isEqualTo("NEARBY");
        assertThat(osoba(lista, "lo_krakow").get("proximity").isNull()).isTrue();
        assertThat(osoba(lista, "lo_bez").get("proximity").isNull()).isTrue();
        assertThat(osoba(lista, "lo_swarzedz").get("city").asText()).isEqualTo("Swarzędz");

        // sama bliskosc to nie "dopasowanie" - karta nie ma powodow do pokazania
        assertThat(osoba(lista, "lo_poznan").get("matched").asBoolean()).isFalse();
        // brak wlasnych odleglosci w odpowiedzi
        assertThat(osoba(lista, "lo_poznan").has("distanceKm")).isFalse();
    }

    @Test
    @DisplayName("propozycje: wspolni artysci wciaz waza wiecej niz sama okolica")
    void tasteStillMatters() throws Exception {
        miasto("lo_ja", "Poznań");
        miasto("lo_poznan", "Poznań");
        miasto("lo_krakow", "Kraków");
        Artist a = artists.save(new Artist("lo-a", "Artysta A", null));
        Artist b = artists.save(new Artist("lo-b", "Artysta B", null));
        for (String login : List.of("lo_ja", "lo_krakow")) {
            User u = users.findByUsername(login).orElseThrow();
            u.getFavoriteArtists().add(a);
            u.getFavoriteArtists().add(b);
        }
        em.flush();

        JsonNode lista = propozycje("lo_ja");
        // dwoch wspolnych artystow (10 pkt) kontra to samo miasto (5 pkt)
        assertThat(lokalni(lista).subList(0, 2)).containsExactly("lo_krakow", "lo_poznan");
        assertThat(osoba(lista, "lo_krakow").get("matched").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("propozycje: kto ukryl miasto albo ma profil dla znajomych, nie ma podpisu, ale kolejnosc zostaje")
    void hiddenCityHasNoLabel() throws Exception {
        miasto("lo_ja", "Poznań");
        miasto("lo_poznan", "Poznań");
        miasto("lo_swarzedz", "Swarzędz");
        User poznan = users.findByUsername("lo_poznan").orElseThrow();
        poznan.setPrivacy(ProfileVisibility.EVERYONE, poznan.getFriendRequestsFrom(), poznan.getClanInvitesFrom(),
            true, true, false, false);
        User swarzedz = users.findByUsername("lo_swarzedz").orElseThrow();
        swarzedz.setPrivacy(ProfileVisibility.FRIENDS, swarzedz.getFriendRequestsFrom(), swarzedz.getClanInvitesFrom(),
            true, true, false, true);
        em.flush();

        JsonNode lista = propozycje("lo_ja");
        assertThat(lokalni(lista).subList(0, 2)).containsExactly("lo_poznan", "lo_swarzedz");
        assertThat(osoba(lista, "lo_poznan").get("proximity").isNull()).isTrue();
        assertThat(osoba(lista, "lo_poznan").get("city").isNull()).isTrue();
        assertThat(osoba(lista, "lo_swarzedz").get("proximity").isNull()).isTrue();
        assertThat(osoba(lista, "lo_swarzedz").get("city").isNull()).isTrue();
    }

    @Test
    @DisplayName("propozycje: miasto spoza listy to tylko 'to samo miasto', bez odleglosci do innych")
    void unknownCityOnlyMatchesItself() throws Exception {
        miasto("lo_ja", "Zalesie Dolne");
        miasto("lo_poznan", "Zalesie Dolne");
        miasto("lo_swarzedz", "Poznań");

        JsonNode lista = propozycje("lo_ja");
        assertThat(lokalni(lista).get(0)).isEqualTo("lo_poznan");
        assertThat(osoba(lista, "lo_poznan").get("proximity").asText()).isEqualTo("SAME_CITY");
        assertThat(osoba(lista, "lo_swarzedz").get("proximity").isNull()).isTrue();
    }

    @Test
    @DisplayName("baza i Java licza ten sam poziom bliskosci (progi w SQL propozycji = LocationScore)")
    void sqlAndJavaAgree() throws Exception {
        miasto("lo_ja", "Poznań");
        // miasta w roznych odleglosciach od Poznania: Swarzedz ~11, Gniezno ~47, Konin ~ 100, Lodz ~ 200, Kielce ~ 380
        Map<String, String> miasta = Map.of("lo_poznan", "Poznań", "lo_swarzedz", "Swarzędz", "lo_gniezno", "Gniezno",
            "lo_krakow", "Konin", "lo_bez", "Łódź");
        miasta.forEach((login, m) -> {
            try {
                miasto(login, m);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        JsonNode lista = propozycje("lo_ja");
        // kolejnosc zgodna z poziomami liczonymi w Javie z tych samych wspolrzednych
        CityIndex index = new CityIndex();
        index.load();
        CityIndex.City ja = index.find("Poznań").orElseThrow();
        List<String> oczekiwane = miasta.entrySet().stream()
            .sorted((x, y) -> {
                int lx = LocationScore.level(CityIndex.distanceKm(ja.latitude(), ja.longitude(),
                    index.find(x.getValue()).orElseThrow().latitude(), index.find(x.getValue()).orElseThrow().longitude()));
                int ly = LocationScore.level(CityIndex.distanceKm(ja.latitude(), ja.longitude(),
                    index.find(y.getValue()).orElseThrow().latitude(), index.find(y.getValue()).orElseThrow().longitude()));
                return lx != ly ? Integer.compare(ly, lx) : x.getKey().compareTo(y.getKey());
            })
            .map(Map.Entry::getKey).toList();
        assertThat(lokalni(lista).subList(0, oczekiwane.size())).containsExactlyElementsOf(oczekiwane);
    }

    /* ------------------------------ wydarzenia ------------------------------ */

    private LocalDate za(int dni) {
        return LocalDate.now(ZoneId.of("Europe/Warsaw")).plusDays(dni);
    }

    private MusicEvent koncert(String id, String nazwa, int zaIleDni, String miasto, Double lat, Double lon,
                               String... sklad) {
        MusicEvent e = new MusicEvent(id);
        e.describe(nazwa, null, null, "Rock", null);
        e.schedule(za(zaIleDni), LocalTime.of(20, 0));
        e.place("V-" + id, "Klub " + id, miasto, EventImportService.cityKey(miasto), null, lat, lon);
        e.link(null, null, null);
        e.groupAs(EventImportService.seriesKey(nazwa, "V-" + id, "Klub " + id));
        e.replacePerformers(java.util.Arrays.stream(sklad).map(s -> new EventPerformer(null, s)).toList());
        e.inCountry("PL");
        e.markSeen(LocalDateTime.now());
        return events.save(e);
    }

    /**
     * Daty celowo odwrotnie niz odleglosc od Poznania: im dalej, tym wczesniej. Dzieki temu kolejnosc
     * "po dacie" i "po bliskosci" sie roznia i widac, ktora z nich naprawde obowiazuje.
     */
    private void koncerty() {
        koncert("lo-e3", "Koncert w Krakowie", 3, "Krakow", 50.06, 19.94, "Zespol Lokalny");
        koncert("lo-e4", "Koncert w Warszawie", 4, "Warsaw", null, null, "Zespol Lokalny");
        koncert("lo-e5", "Koncert na wsi", 5, "Zalesie Dolne", null, null, "Zespol Lokalny");
        koncert("lo-e2", "Koncert w Gnieznie", 6, "Gniezno", 52.53, 17.58, "Zespol Lokalny");
        koncert("lo-e1", "Koncert w Poznaniu", 7, "Poznan", 52.40, 16.92, "Zespol Lokalny");
        em.flush();
    }

    private JsonNode wydarzenia(String kto, String parametry) throws Exception {
        return tresc(get_(kto, "/api/events?view=UPCOMING&size=50" + parametry).andExpect(status().isOk()));
    }

    private static List<String> nazwy(JsonNode strona) {
        return teksty(strona.get("content"), "name");
    }

    @Test
    @DisplayName("wydarzenia z promieniem: tylko w okolicy; bez promienia albo bez miasta - wszystkie")
    void radiusFilter() throws Exception {
        koncerty();
        miasto("lo_ja", "Poznań");

        assertThat(nazwy(wydarzenia("lo_ja", "&radius=30"))).containsExactly("Koncert w Poznaniu");
        // lista "Najblizsze" idzie po dacie, wiec wczesniejsze Gniezno jest przed Poznaniem
        assertThat(nazwy(wydarzenia("lo_ja", "&radius=100"))).containsExactly("Koncert w Gnieznie", "Koncert w Poznaniu");
        // Kraków (~ 335 km) odpada, a Warszawa (~ 280 km, bez wspolrzednych - polozenie z listy miast) zostaje
        assertThat(nazwy(wydarzenia("lo_ja", "&radius=300")))
            .containsExactly("Koncert w Warszawie", "Koncert w Gnieznie", "Koncert w Poznaniu");
        assertThat(nazwy(wydarzenia("lo_ja", ""))).hasSize(5);
        assertThat(nazwy(wydarzenia("lo_ja", "&radius=0"))).hasSize(5);
        // kto nie ma miasta, nie ma od czego liczyc - promien nic nie odcina
        assertThat(nazwy(wydarzenia("lo_bez", "&radius=30"))).hasSize(5);
        assertThat(wydarzenia("lo_ja", "&radius=100").get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("karta wydarzenia niesie odleglosc: 0 w tym samym miescie, w km dalej, pusta bez miasta")
    void cardDistance() throws Exception {
        koncerty();
        miasto("lo_ja", "Poznań");
        JsonNode karty = wydarzenia("lo_ja", "").get("content");
        Map<String, JsonNode> poNazwie = new java.util.HashMap<>();
        karty.forEach(k -> poNazwie.put(k.get("name").asText(), k));

        assertThat(poNazwie.get("Koncert w Poznaniu").get("distanceKm").asInt()).isZero();
        assertThat(poNazwie.get("Koncert w Gnieznie").get("distanceKm").asInt()).isBetween(40, 55);
        assertThat(poNazwie.get("Koncert w Krakowie").get("distanceKm").asInt()).isBetween(320, 350);
        // bez wspolrzednych wydarzenia - z listy miast ("Warsaw" = Warszawa)
        assertThat(poNazwie.get("Koncert w Warszawie").get("distanceKm").asInt()).isBetween(260, 300);
        assertThat(poNazwie.get("Koncert na wsi").get("distanceKm").isNull()).isTrue();

        JsonNode bez = wydarzenia("lo_bez", "").get("content");
        bez.forEach(k -> assertThat(k.get("distanceKm").isNull()).isTrue());
    }

    @Test
    @DisplayName("miasto spoza listy: promien dziala tylko jako 'to samo miasto'")
    void radiusWithUnlocatedCity() throws Exception {
        koncerty();
        miasto("lo_ja", "Zalesie Dolne");
        assertThat(nazwy(wydarzenia("lo_ja", "&radius=100"))).containsExactly("Koncert na wsi");
        JsonNode info = tresc(get_("lo_ja", "/api/events/info"));
        assertThat(info.get("myCity").asText()).isEqualTo("Zalesie Dolne");
        assertThat(info.get("myCityLocated").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Dla ciebie: przy tym samym guscie blizszy koncert wyzej; sama okolica nie wciaga koncertow bez dopasowania")
    void forYouPrefersNearby() throws Exception {
        koncerty();
        koncert("lo-e6", "Niepasujacy w Poznaniu", 2, "Poznan", 52.40, 16.92, "Ktos Inny");
        miasto("lo_ja", "Poznań");
        Artist lokalny = artists.save(new Artist("lo-zl", "Zespol Lokalny", null));
        users.findByUsername("lo_ja").orElseThrow().getFavoriteArtists().add(lokalny);
        em.flush();

        JsonNode strona = tresc(get_("lo_ja", "/api/events?view=FOR_YOU&size=50").andExpect(status().isOk()));
        // Poznan (+40), Gniezno (+24), potem wszystkie dalej niz 250 km albo bez polozenia - po dacie.
        // Gdyby bonus nie dzialal, Poznan i Gniezno (najpozniejsze) byly by na koncu.
        assertThat(nazwy(strona)).containsExactly(
            "Koncert w Poznaniu", "Koncert w Gnieznie", "Koncert w Krakowie", "Koncert w Warszawie", "Koncert na wsi");
        assertThat(nazwy(strona)).doesNotContain("Niepasujacy w Poznaniu");

        // ten sam gust, ale bez miasta: kolejnosc po dacie (jak dotad)
        users.findByUsername("lo_bez").orElseThrow().getFavoriteArtists().add(lokalny);
        em.flush();
        JsonNode bez = tresc(get_("lo_bez", "/api/events?view=FOR_YOU&size=50").andExpect(status().isOk()));
        assertThat(nazwy(bez)).containsExactly(
            "Koncert w Krakowie", "Koncert w Warszawie", "Koncert na wsi", "Koncert w Gnieznie", "Koncert w Poznaniu");

        // Dla ciebie z promieniem
        JsonNode bliskie = tresc(get_("lo_ja", "/api/events?view=FOR_YOU&radius=100").andExpect(status().isOk()));
        assertThat(nazwy(bliskie)).containsExactly("Koncert w Poznaniu", "Koncert w Gnieznie");
    }

    @Test
    @DisplayName("strona wydarzenia mowi, ile km od miasta z profilu")
    void detailsDistance() throws Exception {
        koncerty();
        miasto("lo_ja", "Poznań");
        Long gniezno = events.findByExternalIdIn(List.of("lo-e2")).get(0).getId();
        JsonNode szczegoly = tresc(get_("lo_ja", "/api/events/" + gniezno).andExpect(status().isOk()));
        assertThat(szczegoly.get("distanceKm").asInt()).isBetween(40, 55);
        assertThat(tresc(get_("lo_bez", "/api/events/" + gniezno)).get("distanceKm").isNull()).isTrue();
    }

    /* -------------------------------- klany -------------------------------- */

    private long klan(String zalozyciel, String nazwa, String skrot, String miasto) throws Exception {
        Map<String, Object> cialo = new java.util.HashMap<>(Map.of("name", nazwa, "tag", skrot, "listed", true));
        if (miasto != null) {
            cialo.put("city", miasto);
        }
        long id = tresc(post_(zalozyciel, "/api/clans", cialo).andExpect(status().isCreated())).get("id").asLong();
        em.flush();
        return id;
    }

    private JsonNode katalog(String kto, String parametry) throws Exception {
        return tresc(get_(kto, "/api/clans/directory?size=50" + parametry).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("przegladarka klanow: promien, sortowanie od najblizszego i liczba klanow bez znanego miejsca")
    void clanDirectoryByDistance() throws Exception {
        klan("lo_poznan", "Poznanskie Brzmienia", "PZN", "Poznań");
        klan("lo_swarzedz", "Swarzedzkie Granie", "SWZ", "Swarzędz");
        klan("lo_gniezno", "Gnieznienska Banda", "GNZ", "Gniezno");
        klan("lo_krakow", "Krakowski Klub", "KRK", "Kraków");
        klan("lo_bez", "Klan Bez Miasta", "BEZ", null);
        miasto("lo_ja", "Poznań");

        JsonNode wszystkie = katalog("lo_ja", "&sort=NEAREST");
        assertThat(nazwy(wszystkie)).containsExactly("Poznanskie Brzmienia", "Swarzedzkie Granie",
            "Gnieznienska Banda", "Krakowski Klub", "Klan Bez Miasta");
        JsonNode pierwszy = wszystkie.get("content").get(0);
        assertThat(pierwszy.get("distanceKm").asInt()).isZero();
        assertThat(wszystkie.get("content").get(1).get("distanceKm").asInt()).isBetween(5, 20);
        assertThat(wszystkie.get("content").get(4).get("distanceKm").isNull()).isTrue();
        assertThat(wszystkie.get("withoutLocation").asInt()).isZero();

        JsonNode wOkolicy = katalog("lo_ja", "&radius=100&sort=NEAREST");
        assertThat(nazwy(wOkolicy)).containsExactly("Poznanskie Brzmienia", "Swarzedzkie Granie", "Gnieznienska Banda");
        assertThat(wOkolicy.get("total").asInt()).isEqualTo(3);
        // klan bez miasta nie wiadomo gdzie jest - nie ma go na liscie, ale wiadomo, ze jest
        assertThat(wOkolicy.get("withoutLocation").asInt()).isEqualTo(1);

        // kto nie ma miasta, widzi wszystko
        assertThat(nazwy(katalog("lo_bez", "&radius=100"))).hasSize(5);
        // inne filtry zawezaja tez liczbe "bez miejsca"
        assertThat(katalog("lo_ja", "&radius=100&q=krakow").get("withoutLocation").asInt()).isZero();
    }

    @Test
    @DisplayName("przegladarka klanow: przy 'najlepiej pasujace' ten sam gust = blizszy klan wyzej")
    void clanMatchPrefersNearby() throws Exception {
        klan("lo_krakow", "Aaa Daleko", "AAA", "Kraków");
        klan("lo_poznan", "Zzz Blisko", "ZZZ", "Poznań");
        miasto("lo_ja", "Poznań");
        // nazwy tak dobrane, zeby alfabet dawal odwrotna kolejnosc niz bliskosc
        assertThat(nazwy(katalog("lo_ja", "&sort=MATCH"))).containsExactly("Zzz Blisko", "Aaa Daleko");
        assertThat(nazwy(katalog("lo_bez", "&sort=MATCH"))).containsExactly("Aaa Daleko", "Zzz Blisko");
    }
}
