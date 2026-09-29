package com.musicclubapp.service;

import com.musicclubapp.dto.AttendeeResponse;
import com.musicclubapp.dto.ParticipationResponse;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.musicclubapp.entity.ParticipationStatus.GOING;
import static com.musicclubapp.entity.ParticipationStatus.INTERESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "Zainteresowany", "Biore udzial", rezygnacja i lista uczestnikow. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Zapisy na wydarzenia")
class EventParticipationServiceTest {

    @Autowired private MusicEventRepository eventRepository;
    @Autowired private EventParticipationRepository participationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PerformerTagService performerTagService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserModerationService moderationService;
    @Autowired private EntityManager entityManager;

    private EventParticipationService zapisy;

    private User ala;
    private User bob;
    private User ola;
    private MusicEvent koncert;

    @BeforeEach
    void setUp() {
        Clock zegar = Clock.fixed(EventImportServiceTest.TERAZ, ZoneOffset.UTC);
        EventImportService importer = new EventImportService(new TicketmasterClient("", "http://127.0.0.1:9", 500), eventRepository, participationRepository,
            performerTagService, transactionManager, zegar, 0, EventImportServiceTest.SZESC_GODZIN);
        zapisy = new EventParticipationService(participationRepository, eventRepository,
            userRepository, importer, zegar);

        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        ola = userRepository.save(new User("ola", "ola@example.com", "hash"));
        koncert = eventRepository.save(WydarzeniaTestowe.wydarzenie("k1", "Koncert", LocalDate.of(2026, 10, 10),
            "Warsaw", "Rock", "Indie Rock", "Zespol"));
    }

    @Test
    @DisplayName("zainteresowany, potem ide - jeden zapis, ktory zmienia stan")
    void interestedThenGoing() {
        ParticipationResponse po1 = zapisy.participate(koncert.getId(), "ala", INTERESTED, false);
        assertThat(po1.myStatus()).isEqualTo(INTERESTED);
        assertThat(po1.interested()).isEqualTo(1);
        assertThat(po1.going()).isZero();

        ParticipationResponse po2 = zapisy.participate(koncert.getId(), "ala", GOING, false);
        assertThat(po2.myStatus()).isEqualTo(GOING);
        assertThat(po2.going()).isEqualTo(1);
        assertThat(po2.interested()).isZero();
        assertThat(participationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("rezygnacja usuwa zapis - liczniki wracaja do zera")
    void cancel() {
        zapisy.participate(koncert.getId(), "ala", GOING, false);

        ParticipationResponse po = zapisy.cancel(koncert.getId(), "ala");

        assertThat(po.myStatus()).isNull();
        assertThat(po.going()).isZero();
        assertThat(participationRepository.count()).isZero();
        // Druga rezygnacja nie jest bledem - moze to byc drugie klikniecie
        assertThat(zapisy.cancel(koncert.getId(), "ala").myStatus()).isNull();
    }

    @Test
    @DisplayName("\"nie pokazuj mnie\": liczy sie do licznika, ale nie ma go na liscie - poza nim samym")
    void hiddenAttendee() {
        zapisy.participate(koncert.getId(), "ala", GOING, true);
        zapisy.participate(koncert.getId(), "bob", GOING, false);

        List<AttendeeResponse> widziOla = zapisy.attendees(koncert.getId(), "ola", PageRequest.of(0, 10)).getContent();
        assertThat(widziOla).extracting(AttendeeResponse::username).containsExactly("bob");

        List<AttendeeResponse> widziAla = zapisy.attendees(koncert.getId(), "ala", PageRequest.of(0, 10)).getContent();
        assertThat(widziAla).extracting(AttendeeResponse::username).containsExactly("ala", "bob");
        assertThat(widziAla.get(0).me()).isTrue();
        assertThat(widziAla.get(0).hidden()).isTrue();

        ParticipationResponse liczniki = zapisy.summary(koncert.getId(), "ola");
        assertThat(liczniki.going()).isEqualTo(2);
        assertThat(liczniki.hiddenGoing()).isEqualTo(1);
    }

    @Test
    @DisplayName("na liscie tylko ci, ktorzy ida - zainteresowani sa sama liczba")
    void onlyGoingAreListed() {
        zapisy.participate(koncert.getId(), "ala", INTERESTED, false);
        zapisy.participate(koncert.getId(), "bob", GOING, false);

        assertThat(zapisy.attendees(koncert.getId(), "ola", PageRequest.of(0, 10)).getContent())
            .extracting(AttendeeResponse::username).containsExactly("bob");
    }

    @Test
    @DisplayName("kolejnosc listy: ja, potem znajomi, potem reszta")
    void attendeeOrder() {
        User cezary = userRepository.save(new User("cezary", "c@example.com", "hash"));
        ola.addFriend(cezary);
        entityManager.flush();

        zapisy.participate(koncert.getId(), "bob", GOING, false);
        zapisy.participate(koncert.getId(), "cezary", GOING, false);
        zapisy.participate(koncert.getId(), "ola", GOING, false);

        List<AttendeeResponse> lista = zapisy.attendees(koncert.getId(), "ola", PageRequest.of(0, 10)).getContent();

        assertThat(lista).extracting(AttendeeResponse::username).containsExactly("ola", "cezary", "bob");
        assertThat(lista.get(1).friend()).isTrue();
        assertThat(lista.get(2).friend()).isFalse();
    }

    @Test
    @DisplayName("na minione wydarzenie nie da sie zapisac, ale da sie zrezygnowac")
    void pastEvent() {
        MusicEvent minione = eventRepository.save(WydarzeniaTestowe.wydarzenie("stare", "Stare",
            LocalDate.of(2026, 9, 20), "Warsaw", null, null));

        assertThatThrownBy(() -> zapisy.participate(minione.getId(), "ala", GOING, false))
            .isInstanceOf(OperationNotAllowedException.class);
        assertThat(zapisy.cancel(minione.getId(), "ala").myStatus()).isNull();
    }

    @Test
    @DisplayName("na wycofane nowych zapisow nie ma; zapisany moze zrezygnowac")
    void withdrawnEvent() {
        zapisy.participate(koncert.getId(), "ala", GOING, false);
        koncert.withdraw(LocalDateTime.of(2026, 9, 28, 12, 0));
        entityManager.flush();

        assertThatThrownBy(() -> zapisy.participate(koncert.getId(), "bob", INTERESTED, false))
            .isInstanceOf(OperationNotAllowedException.class);
        assertThat(zapisy.cancel(koncert.getId(), "ala").myStatus()).isNull();
    }

    @Test
    @DisplayName("usuniecie konta zabiera jego zapisy - nie zostaje \"ktos\" na liscie")
    void accountDeletionRemovesParticipation() {
        zapisy.participate(koncert.getId(), "ala", GOING, false);
        zapisy.participate(koncert.getId(), "bob", GOING, false);
        entityManager.flush();

        moderationService.deleteUser("admin", ala.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(participationRepository.count()).isEqualTo(1);
        assertThat(zapisy.summary(koncert.getId(), "ola").going()).isEqualTo(1);
    }

    @Test
    @DisplayName("jeden zapis na osobe i wydarzenie pilnuje takze baza")
    void uniquePerUserAndEvent() {
        zapisy.participate(koncert.getId(), "ala", INTERESTED, false);
        zapisy.participate(koncert.getId(), "ala", INTERESTED, true);
        zapisy.participate(koncert.getId(), "ala", GOING, true);
        entityManager.flush();

        assertThat(participationRepository.count()).isEqualTo(1);
        assertThat(zapisy.summary(koncert.getId(), "ala").hidden()).isTrue();
    }

    @Test
    @DisplayName("stan zapisu dla kogos, kto nic nie zaznaczyl, jest pusty")
    void noParticipation() {
        ParticipationResponse stan = zapisy.summary(koncert.getId(), "ola");
        assertThat(stan.myStatus()).isNull();
        assertThat(stan.hidden()).isFalse();
    }
}
