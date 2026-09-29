package com.musicclubapp.service;

import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventDetailsResponse;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.MusicEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lista wydarzen, strona wydarzenia, miasta. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Wydarzenia - lista, szukanie, szczegoly")
class EventServiceTest {

    @Autowired private MusicEventRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestHttpServer server;
    private EventImportService importer;
    private EventService events;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        String polska = TicketmasterClientTest.odpowiedzZPolski();
        server.odpowiadaj(TicketmasterClientTest.SCIEZKA, query -> TestHttpServer.Odpowiedz.ok(
            query.contains(EventImportServiceTest.PIERWSZE_OKNO) ? polska : EventImportServiceTest.PUSTO));

        TicketmasterClient ticketmaster =
            new TicketmasterClient("klucz", server.url() + "/discovery/v2", 2000);
        importer = new EventImportService(ticketmaster, repository, transactionManager,
            Clock.fixed(EventImportServiceTest.TERAZ, ZoneOffset.UTC), 0);
        events = new EventService(repository, importer);

        importer.runImport();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private Page<EventCardResponse> lista(String city, String q) {
        return events.list(city, q, PageRequest.of(0, 20));
    }

    private List<String> nazwy(Page<EventCardResponse> page) {
        return page.getContent().stream().map(EventCardResponse::name).toList();
    }

    private Long id(String externalId) {
        return repository.findByExternalIdIn(List.of(externalId)).get(0).getId();
    }

    @Test
    @DisplayName("od najblizszego; ta sama nazwa w tym samym miejscu to jedna karta")
    void listGroupsSeries() {
        Page<EventCardResponse> page = lista("", "");

        assertThat(nazwy(page)).containsExactly(
            "Koncert przy świecach",                                              // 29.09 18:00
            "The Amity Affliction | Support: Silent Planet, Varials, Orthodox",  // 29.09 19:00
            "Vundabar and Yot Club",                                              // 29.09 20:00
            "Nachtmahr | Special guests: Cygnosic, Bagger 258",                  // 01.10 19:30
            "Globus Jazz Live",                                                   // 01.10 20:00
            "Jack Savoretti - WE WILL ALWAYS BE THE WAY WE WERE",                 // 02.10 20:00
            "Strachy na Lachy",                                                   // 02.10 20:00
            "MROZU - Odpowiedni moment tour");                                    // 02.10, godzina nieznana

        assertThat(page.getTotalElements()).isEqualTo(8);

        EventCardResponse swiece = page.getContent().get(0);
        assertThat(swiece.date()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(swiece.time()).isEqualTo(LocalTime.of(18, 0));
        // Trzy terminy: ten na karcie i dwa kolejne
        assertThat(swiece.moreDates()).isEqualTo(2);
        assertThat(page.getContent().get(1).moreDates()).isZero();
    }

    @Test
    @DisplayName("karta niesie to, co widac na liscie: miejsce, miasto, sklad, miniature")
    void cardContent() {
        EventCardResponse amity = lista("", "amity").getContent().get(0);

        assertThat(amity.venueName()).isEqualTo("Progresja");
        assertThat(amity.cityKey()).isEqualTo("warsaw");
        assertThat(amity.performers()).containsExactly("The Amity Affliction", "Silent Planet", "Varials", "Orthodox");
        assertThat(amity.thumbUrl()).endsWith("_16_9_640x360.jpg");
        assertThat(amity.genre()).isEqualTo("Rock");
    }

    @Test
    @DisplayName("filtr miasta: Kraków i Krakow to jedno miasto, a Łódź dziala z polskimi znakami")
    void cityFilter() {
        assertThat(nazwy(lista("krakow", "")))
            .containsExactly("Globus Jazz Live", "Jack Savoretti - WE WILL ALWAYS BE THE WAY WE WERE");
        assertThat(nazwy(lista("Kraków", ""))).hasSize(2);
        assertThat(nazwy(lista("Łódź", ""))).containsExactly("MROZU - Odpowiedni moment tour");
        // "Warszawa" i "Warsaw" z Ticketmastera tez sa jednym miastem
        assertThat(nazwy(lista("warsaw", ""))).hasSize(3);
    }

    @Test
    @DisplayName("szukanie po nazwie, po miejscu i po kims z supportu")
    void search() {
        assertThat(nazwy(lista("", "silent planet")))
            .containsExactly("The Amity Affliction | Support: Silent Planet, Varials, Orthodox");
        assertThat(nazwy(lista("", "TAMA"))).containsExactly("Strachy na Lachy");
        assertThat(nazwy(lista("", "ŚWIECACH"))).containsExactly("Koncert przy świecach");
        assertThat(nazwy(lista("warsaw", "jazz"))).isEmpty();
    }

    @Test
    @DisplayName("% i _ to zwykle znaki, a nie \"cokolwiek\"")
    void likeWildcardsAreLiteral() {
        assertThat(lista("", "%").getTotalElements()).isZero();
        assertThat(lista("", "_").getTotalElements()).isZero();
        assertThat(EventService.likePattern("100%_ok\\")).isEqualTo("%100\\%\\_ok\\\\%");
    }

    @Test
    @DisplayName("strony: licznik liczy karty, nie pojedyncze terminy")
    void paging() {
        Page<EventCardResponse> pierwsza = events.list("", "", PageRequest.of(0, 3));
        Page<EventCardResponse> ostatnia = events.list("", "", PageRequest.of(2, 3));

        assertThat(pierwsza.getContent()).hasSize(3);
        assertThat(pierwsza.getTotalElements()).isEqualTo(8);
        assertThat(pierwsza.getTotalPages()).isEqualTo(3);
        assertThat(nazwy(ostatnia)).containsExactly("Strachy na Lachy", "MROZU - Odpowiedni moment tour");
        assertThat(ostatnia.isLast()).isTrue();
    }

    @Test
    @DisplayName("minione wydarzenia nie sa na liscie, ale ich strona dalej dziala")
    void pastEvents() {
        MusicEvent minione = repository.save(EventImportServiceTest.stare("minione", LocalDate.of(2026, 9, 27)));

        assertThat(nazwy(lista("", "minione"))).isEmpty();

        EventDetailsResponse szczegoly = events.details(minione.getId());
        assertThat(szczegoly.past()).isTrue();
    }

    @Test
    @DisplayName("strona wydarzenia: pelne dane i pozostale terminy - bez siebie samego")
    void details() {
        EventDetailsResponse drugi = events.details(id("Z698xZbpZ17Can2"));

        assertThat(drugi.date()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(drugi.time()).isEqualTo(LocalTime.of(20, 30));
        assertThat(drugi.otherDates()).extracting(d -> d.date())
            .containsExactly(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1));
        assertThat(drugi.past()).isFalse();

        EventDetailsResponse amity = events.details(id("vvG1zZ9KSAmity"));
        assertThat(amity.imageUrl()).endsWith("_16_9_1024x576.jpg");
        assertThat(amity.address()).isEqualTo("Fort Wola 22");
        assertThat(amity.latitude()).isEqualTo(52.2321);
        assertThat(amity.description()).startsWith("Australijczycy");
        assertThat(amity.ticketUrl()).startsWith("https://www.ticketmaster.pl/event/");
        assertThat(amity.otherDates()).isEmpty();
    }

    @Test
    @DisplayName("nieistniejace wydarzenie to 404, a nie pusta strona")
    void missingEvent() {
        assertThatThrownBy(() -> events.details(987654L))
            .isInstanceOf(NoSuchElementFoundException.class);
    }

    @Test
    @DisplayName("miasta: od najbardziej ruchliwego, liczone kartami")
    void cities() {
        EventsInfoResponse info = events.info(false);

        assertThat(info.configured()).isTrue();
        assertThat(info.cities()).extracting(c -> c.key())
            .containsExactly("warsaw", "krakow", "lodz", "poznan", "wroclaw");
        // Swiece graja trzy razy, ale na liscie to jedna karta
        assertThat(info.cities().get(0).events()).isEqualTo(3);
    }

    @Test
    @DisplayName("stan importu widzi tylko administrator")
    void importStatusOnlyForAdmin() {
        assertThat(events.info(false).lastImport()).isNull();

        EventsInfoResponse.ImportInfo stan = events.info(true).lastImport();
        assertThat(stan).isNotNull();
        assertThat(stan.success()).isTrue();
        assertThat(stan.events()).isEqualTo(10);
    }
}
