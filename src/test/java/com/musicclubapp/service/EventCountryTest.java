package com.musicclubapp.service;

import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventView;
import com.musicclubapp.dto.EventsInfoResponse;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kraj wydarzen: wybor na koncie, import wielu krajow, limit zapytan. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Wydarzenia - wybor kraju")
class EventCountryTest {

    /** Jeden koncert w Berlinie - tyle wystarczy, zeby odroznic Niemcy od Polski. */
    private static final String BERLIN = """
        {"_embedded":{"events":[{
          "name":"Rammstein","type":"event","id":"de-1","test":false,
          "url":"https://www.ticketmaster.de/event/de-1",
          "dates":{"start":{"localDate":"2026-10-03","localTime":"20:00:00"},"status":{"code":"onsale"}},
          "classifications":[{"primary":true,"genre":{"name":"Rock"},"subGenre":{"name":"Metal"}}],
          "_embedded":{
            "venues":[{"name":"Olympiastadion","id":"V-DE-1","city":{"name":"Berlin"},
                       "country":{"name":"Germany","countryCode":"DE"}}],
            "attractions":[{"name":"Rammstein","id":"A-DE-1"}]}
        }]},"page":{"size":200,"totalElements":1,"totalPages":1,"number":0}}
        """;

    @Autowired private BlockService blocks;
    @Autowired private MusicEventRepository repository;
    @Autowired private EventParticipationRepository participationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PerformerTagService performerTagService;
    @Autowired private EventMatchService matchService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private TestHttpServer server;
    private TicketmasterClient ticketmaster;
    private Clock zegar;
    private String polska;

    @BeforeEach
    void setUp() throws IOException {
        server = new TestHttpServer();
        ticketmaster = new TicketmasterClient("klucz", server.url() + "/discovery/v2", 2000);
        zegar = Clock.fixed(EventImportServiceTest.TERAZ, ZoneOffset.UTC);
        polska = TicketmasterClientTest.odpowiedzZPolski();
        server.odpowiadaj(TicketmasterClientTest.SCIEZKA, query -> {
            if (!query.contains(EventImportServiceTest.PIERWSZE_OKNO)) {
                return TestHttpServer.Odpowiedz.ok(EventImportServiceTest.PUSTO);
            }
            return TestHttpServer.Odpowiedz.ok(query.contains("countryCode=DE") ? BERLIN
                : query.contains("countryCode=PL") ? polska : EventImportServiceTest.PUSTO);
        });

        User ola = userRepository.save(new User("ola", "ola@example.com", "hash"));
        ola.setEventsCountry("DE");
        userRepository.save(new User("ala", "ala@example.com", "hash"));
        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private EventImportService importer(int limitNaKraj) {
        return WydarzeniaTestowe.importer(ticketmaster, repository, participationRepository,
            performerTagService, userRepository, transactionManager, zegar, limitNaKraj);
    }

    private EventService events(EventImportService importer) {
        EventParticipationService zapisy = new EventParticipationService(participationRepository, repository,
            userRepository, importer, zegar, blocks);
        return new EventService(repository, participationRepository, userRepository, importer,
            matchService, zapisy, performerTagService);
    }

    private List<String> nazwy(EventService events, String kto) {
        return events.list(EventView.UPCOMING, "", "", PageRequest.of(0, 50), kto).getContent().stream()
            .map(EventCardResponse::name).toList();
    }

    @Test
    @DisplayName("import obejmuje Polske i kraje wybrane na kontach")
    void importsChosenCountries() {
        EventImportService importer = importer(1000);

        assertThat(importer.activeCountries()).containsExactly("PL", "DE");
        importer.runImport();

        assertThat(server.requests()).anyMatch(r -> r.contains("countryCode=PL"));
        assertThat(server.requests()).anyMatch(r -> r.contains("countryCode=DE"));
        MusicEvent rammstein = repository.findByExternalIdIn(List.of("de-1")).get(0);
        assertThat(rammstein.getCountryCode()).isEqualTo("DE");
    }

    @Test
    @DisplayName("kazdy widzi wydarzenia ze swojego kraju - i miasta z niego")
    void listIsPerCountry() {
        EventImportService importer = importer(1000);
        importer.runImport();
        EventService events = events(importer);

        assertThat(nazwy(events, "ola")).containsExactly("Rammstein");
        assertThat(nazwy(events, "ala")).hasSize(8).doesNotContain("Rammstein");

        EventsInfoResponse info = events.info(false, "ola");
        assertThat(info.country()).isEqualTo("DE");
        assertThat(info.cities()).extracting(c -> c.key()).containsExactly("berlin");
        assertThat(info.countries()).contains("PL", "DE", "CZ");
    }

    @Test
    @DisplayName("zmiana kraju na koncie; kraju spoza listy nie da sie wybrac")
    void changeCountry() {
        EventService events = events(importer(1000));

        EventsInfoResponse po = events.changeCountry("ala", "cz", false);

        assertThat(po.country()).isEqualTo("CZ");
        assertThat(userRepository.findByUsername("ala").orElseThrow().getEventsCountry()).isEqualTo("CZ");
        assertThatThrownBy(() -> events.changeCountry("ala", "XX", false))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("limit zapytan na kraj zatrzymuje import - i nic poza pokryciem nie znika")
    void budgetLimitsImportAndRemoval() {
        /* Koncert w grudniu, ktorego udawany Ticketmaster nie zwraca - poza zasiegiem limitu. */
        MusicEvent grudzien = WydarzeniaTestowe.wydarzenie("grudzien", "Grudniowy", LocalDate.of(2026, 12, 12),
            "Warsaw", "Rock", "Rock", "Ktos");
        repository.save(grudzien);
        entityManager.flush();

        EventImportService importer = importer(2);
        EventImportService.ImportStatus wynik = importer.importCountries(List.of("PL"));

        // Dwa zapytania: koncowka wrzesnia i pazdziernik - potem limit
        assertThat(server.requests()).hasSize(2);
        assertThat(wynik.success()).isTrue();
        // Grudzien nie byl pokryty, wiec jego brak w odpowiedzi niczego nie znaczy
        assertThat(repository.findByExternalIdIn(List.of("grudzien"))).hasSize(1);
    }

    @Test
    @DisplayName("co kwadrans odswiezaja sie tylko kraje, ktorych dane sie zestarzaly")
    void scheduledImportRefreshesOnlyStaleCountries() {
        EventImportService importer = importer(1000);
        importer.importCountries(List.of("PL"));
        server.requests().clear();

        importer.scheduledImport();

        assertThat(server.requests()).isNotEmpty().allMatch(r -> r.contains("countryCode=DE"));
    }
}
