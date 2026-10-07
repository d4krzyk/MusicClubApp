package com.musicclubapp.service;

import com.musicclubapp.entity.EventStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Czytanie odpowiedzi Ticketmastera. */
@DisplayName("Ticketmaster - czytanie koncertow")
class TicketmasterClientTest {

    static final String SCIEZKA = "/discovery/v2/events.json";
    private static final String KLUCZ = "klucz-tajny-123";

    private TestHttpServer server;
    private TicketmasterClient ticketmaster;

    /** Odpowiedz w formacie Ticketmastera, z koncertami z prawdziwego wyniku dla Polski. */
    static String odpowiedzZPolski() throws IOException {
        try (InputStream in = TicketmasterClientTest.class
                .getResourceAsStream("/ticketmaster/wydarzenia-pl.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        ticketmaster = new TicketmasterClient(KLUCZ, server.url() + "/discovery/v2", 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private Map<String, TicketmasterClient.Event> pobierz() throws IOException {
        server.odpowiadaj(SCIEZKA, odpowiedzZPolski());
        return ticketmaster.events("PL", Instant.parse("2026-09-27T22:00:00Z"),
                Instant.parse("2026-10-31T23:00:00Z"), 0)
            .events().stream()
            .collect(Collectors.toMap(TicketmasterClient.Event::externalId, Function.identity()));
    }

    @Test
    @DisplayName("czyta nazwe, termin, miejsce, sklad i opis")
    void readsEvent() throws IOException {
        TicketmasterClient.Event amity = pobierz().get("vvG1zZ9KSAmity");

        assertThat(amity.name()).isEqualTo("The Amity Affliction | Support: Silent Planet, Varials, Orthodox");
        assertThat(amity.date()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(amity.time()).isEqualTo(LocalTime.of(19, 0));
        assertThat(amity.status()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(amity.venueName()).isEqualTo("Progresja");
        assertThat(amity.city()).isEqualTo("Warsaw");
        assertThat(amity.address()).isEqualTo("Fort Wola 22");
        assertThat(amity.latitude()).isEqualTo(52.2321);
        assertThat(amity.longitude()).isEqualTo(20.9361);
        assertThat(amity.genre()).isEqualTo("Rock");
        assertThat(amity.subGenre()).isEqualTo("Hard Rock");
        assertThat(amity.ticketUrl()).isEqualTo("https://www.ticketmaster.pl/event/vvG1zZ9KSAmity");
        assertThat(amity.description()).startsWith("Australijczycy wracają do Polski");

        // Kolejnosc z plakatu: najpierw gwiazda, potem support
        assertThat(amity.performers()).extracting(TicketmasterClient.Performer::name)
            .containsExactly("The Amity Affliction", "Silent Planet", "Varials", "Orthodox");
    }

    @Test
    @DisplayName("zdjecie: 16:9, najmniejsze nie mniejsze niz potrzeba - duze na strone, mniejsze na liste")
    void picksImages() throws IOException {
        TicketmasterClient.Event amity = pobierz().get("vvG1zZ9KSAmity");

        // W odpowiedzi jest 16:9 w 205, 640, 1024 i 2048 px, do tego 3:2, 4:3 i grafika zastepcza
        assertThat(amity.imageUrl()).endsWith("_16_9_1024x576.jpg");
        assertThat(amity.thumbUrl()).endsWith("_16_9_640x360.jpg");
    }

    @Test
    @DisplayName("grafika zastepcza tylko wtedy, gdy wlasnego zdjecia nie ma")
    void fallbackImageOnlyAsLastResort() throws IOException {
        Map<String, TicketmasterClient.Event> events = pobierz();

        assertThat(events.get("vvG1zZ9KSAmity").imageUrl()).doesNotContain("fallback");
        assertThat(events.get("Z698xZbpZ17Can2").imageUrl()).contains("fallback-classical");
    }

    @Test
    @DisplayName("pomija wydarzenia testowe Ticketmastera i te bez daty")
    void skipsTestAndUndatedEvents() throws IOException {
        Map<String, TicketmasterClient.Event> events = pobierz();

        assertThat(events).doesNotContainKeys("vvG1zZ9TESTEVT", "vvG1zZ9KSTBA");
        assertThat(events).hasSize(10);
    }

    @Test
    @DisplayName("godzina do ustalenia - pusta, a nie polnoc")
    void unknownTimeIsEmpty() throws IOException {
        TicketmasterClient.Event mrozu = pobierz().get("vvG1zZ9KSMroz");

        assertThat(mrozu.date()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(mrozu.time()).isNull();
    }

    @Test
    @DisplayName("miasto bez spacji na koncu - Ticketmaster przysyla \"Łódź \"")
    void trimsCity() throws IOException {
        assertThat(pobierz().get("vvG1zZ9KSMroz").city()).isEqualTo("Łódź");
    }

    @Test
    @DisplayName("odwolany koncert jest oznaczony, a nie pominiety")
    void cancelledEvent() throws IOException {
        assertThat(pobierz().get("vvG1zZ9KSJack").status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    @DisplayName("opis z pola description, gdy nie ma info; \"Undefined\" to nie gatunek")
    void descriptionFallbackAndUndefinedGenre() throws IOException {
        Map<String, TicketmasterClient.Event> events = pobierz();

        assertThat(events.get("vvG1zZ9KSStra").description()).isEqualTo("Grabaż i spółka w poznańskiej Tamie.");
        assertThat(events.get("vvG1zZ9KSGlob").genre()).isNull();
        assertThat(events.get("vvG1zZ9KSGlob").subGenre()).isNull();
    }

    @Test
    @DisplayName("pyta o muzyke w Polsce, 200 na strone, z czasem bez ulamkow sekund")
    void requestParameters() throws IOException {
        server.odpowiadaj(SCIEZKA, odpowiedzZPolski());

        ticketmaster.events("PL", Instant.parse("2026-09-27T22:00:00.123456Z"),
            Instant.parse("2026-10-31T23:00:00Z"), 3);

        String zapytanie = server.requests().get(0);
        assertThat(zapytanie)
            .contains("countryCode=PL")
            .contains("classificationName=music")
            .contains("size=200")
            .contains("page=3")
            .contains("sort=date,asc")
            .contains("startDateTime=2026-09-27T22:00:00Z")
            .contains("endDateTime=2026-10-31T23:00:00Z");
    }

    @Test
    @DisplayName("informacja o stronach - do przechodzenia przez kolejne")
    void pageInfo() throws IOException {
        server.odpowiadaj(SCIEZKA, odpowiedzZPolski());

        TicketmasterClient.Page page = ticketmaster.events("PL", Instant.EPOCH, Instant.EPOCH, 0);

        assertThat(page.totalElements()).isEqualTo(12);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.last()).isTrue();
    }

    @Test
    @DisplayName("zly klucz: blad z komunikatem Ticketmastera - i bez klucza w tresci")
    void invalidKey() {
        server.odpowiadaj(SCIEZKA, query -> new TestHttpServer.Odpowiedz(401,
            "{\"fault\":{\"faultstring\":\"Invalid ApiKey\",\"detail\":{\"errorcode\":\"oauth.v2.InvalidApiKey\"}}}"));

        assertThatThrownBy(() -> ticketmaster.events("PL", Instant.EPOCH, Instant.EPOCH, 0))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("401")
            .hasMessageContaining("Invalid ApiKey")
            .hasMessageNotContaining(KLUCZ)
            // Przyczyna Springa ma w tresci caly adres razem z kluczem - nie moze jej tu byc
            .hasNoCause();
    }

    @Test
    @DisplayName("serwer nie odpowiada: widac prawdziwa przyczyne, ale nie klucz")
    void serverDown() {
        TicketmasterClient niedostepny = new TicketmasterClient(KLUCZ, "http://127.0.0.1:9", 500);

        assertThatThrownBy(() -> niedostepny.events("PL", Instant.EPOCH, Instant.EPOCH, 0))
            .isInstanceOf(IllegalStateException.class)
            // Sama nazwa "ResourceAccessException" nic nie mowila: DNS? certyfikat? czas?
            .hasMessageContaining("ConnectException")
            .hasMessageNotContaining(KLUCZ)
            .hasNoCause();
    }

    @Test
    @DisplayName("przekroczony czas: komunikat mowi, ze chodzi o czas")
    void readTimeout() {
        server.odpowiadaj(SCIEZKA, query -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return TestHttpServer.Odpowiedz.ok("{}");
        });
        TicketmasterClient niecierpliwy = new TicketmasterClient(KLUCZ, server.url() + "/discovery/v2", 300);

        assertThatThrownBy(() -> niecierpliwy.events("PL", Instant.EPOCH, Instant.EPOCH, 0))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SocketTimeoutException")
            .hasMessageNotContaining(KLUCZ);
    }

    @Test
    @DisplayName("gdy w tresci bledu jest caly adres zapytania - klucz i tak znika")
    void keyIsMaskedEvenInsideMessage() {
        // Tak wyglada wyjatek Springa: w tresci pelny adres, razem z kluczem
        Exception e = new org.springframework.web.client.ResourceAccessException(
            "I/O error on GET request for \"https://app.ticketmaster.com/discovery/v2/events.json?apikey="
                + KLUCZ + "&page=0\": zerwane");

        String opis = TicketmasterClient.opisBledu(e, KLUCZ);

        assertThat(opis).doesNotContain(KLUCZ).contains("apikey=***").contains("zerwane");
    }

    @Test
    @DisplayName("bez klucza wydarzenia sa wylaczone - i do sieci nic nie idzie")
    void withoutKey() {
        TicketmasterClient bezKlucza = new TicketmasterClient("  ", server.url() + "/discovery/v2", 2000);

        assertThat(bezKlucza.available()).isFalse();
        assertThatThrownBy(() -> bezKlucza.events("PL", Instant.EPOCH, Instant.EPOCH, 0))
            .isInstanceOf(IllegalStateException.class);
        assertThat(server.requests()).isEmpty();
    }

    @Test
    @DisplayName("opis jednego wykonawcy: /attractions/{id}.json z kluczem, bez tresci licencjonowanych")
    void attractionAbout() {
        server.odpowiadaj("/discovery/v2/attractions/K8vZ917G1W0.json", query -> TestHttpServer.Odpowiedz.ok("""
            { "id": "K8vZ917G1W0", "name": "Kovacs", "type": "attraction", "locale": "en-us",
              "url": "https://www.ticketmaster.pl/artist/kovacs-tickets/950040",
              "description": "Sharon Kovacs is a Dutch singer-songwriter." }
            """));

        var about = ticketmaster.attraction("K8vZ917G1W0");

        assertThat(about).isPresent();
        assertThat(about.get().text()).isEqualTo("Sharon Kovacs is a Dutch singer-songwriter.");
        assertThat(about.get().lang()).isEqualTo("en");
        assertThat(about.get().url()).isEqualTo("https://www.ticketmaster.pl/artist/kovacs-tickets/950040");
        String zapytanie = server.requests().get(0);
        assertThat(zapytanie).contains("/discovery/v2/attractions/K8vZ917G1W0.json").contains("apikey=" + KLUCZ)
            // tresci licencjonowane maja wlasne warunki - nie prosimy o nie
            .doesNotContain("includeLicensedContent");
    }

    @Test
    @DisplayName("opis wykonawcy: brak tekstu albo 404 = pusto; zly numer nie idzie do sieci; blad bez klucza w tresci")
    void attractionWithoutAbout() {
        server.odpowiadaj("/discovery/v2/attractions/PUSTY.json",
            query -> TestHttpServer.Odpowiedz.ok("{ \"id\": \"PUSTY\", \"name\": \"Ktos\" }"));
        server.odpowiadaj("/discovery/v2/attractions/BRAK.json", query -> new TestHttpServer.Odpowiedz(404,
            "{\"errors\":[{\"code\":\"DIS1004\",\"detail\":\"Resource not found\"}]}"));
        server.odpowiadaj("/discovery/v2/attractions/AWARIA.json",
            query -> new TestHttpServer.Odpowiedz(500, "awaria"));

        assertThat(ticketmaster.attraction("PUSTY")).isEmpty();
        assertThat(ticketmaster.attraction("BRAK")).isEmpty();
        int zapytan = server.requests().size();
        assertThat(ticketmaster.attraction("../events")).isEmpty();
        assertThat(ticketmaster.attraction(null)).isEmpty();
        assertThat(server.requests()).hasSize(zapytan);
        assertThatThrownBy(() -> ticketmaster.attraction("AWARIA"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("500")
            .hasMessageNotContaining(KLUCZ)
            .hasNoCause();
    }

    @Test
    @DisplayName("statusy: onsale i offsale to nie odwolanie")
    void statuses() {
        assertThat(List.of("onsale", "offsale", "cancelled", "canceled", "postponed", "rescheduled", "nieznany"))
            .extracting(TicketmasterClient::status)
            .containsExactly(EventStatus.SCHEDULED, EventStatus.SCHEDULED, EventStatus.CANCELLED,
                EventStatus.CANCELLED, EventStatus.POSTPONED, EventStatus.RESCHEDULED, EventStatus.SCHEDULED);
    }
}
