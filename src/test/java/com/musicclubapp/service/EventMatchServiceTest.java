package com.musicclubapp.service;

import com.musicclubapp.dto.EventCardResponse;
import com.musicclubapp.dto.EventReasonKind;
import com.musicclubapp.dto.EventReasonResponse;
import com.musicclubapp.dto.EventView;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.MusicEvent;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.PerformerTags;
import com.musicclubapp.entity.Track;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.EventParticipationRepository;
import com.musicclubapp.repository.MusicEventRepository;
import com.musicclubapp.repository.PerformerTagsRepository;
import com.musicclubapp.repository.TrackRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** "Dla ciebie" - co pasuje do profilu i dlaczego. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Dla ciebie - dopasowanie wydarzen do profilu")
class EventMatchServiceTest {

    @Autowired private LocationService location;
    @Autowired private BlockService blocks;
    @Autowired private NotificationService notificationService;
    @Autowired private MusicEventRepository eventRepository;
    @Autowired private EventParticipationRepository participationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private PerformerTagsRepository performerTagsRepository;
    @Autowired private PerformerTagService performerTagService;
    @Autowired private EventMatchService matchService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private EventService events;
    private EventParticipationService zapisy;

    private MusicEvent strachy;
    private MusicEvent kult;
    private MusicEvent jazz;

    @BeforeEach
    void setUp() {
        Clock zegar = Clock.fixed(EventImportServiceTest.TERAZ, ZoneOffset.UTC);
        EventImportService importer = WydarzeniaTestowe.importer(new TicketmasterClient("", "http://127.0.0.1:9", 500), eventRepository,
            participationRepository, performerTagService, userRepository, transactionManager, zegar);
        zapisy = new EventParticipationService(participationRepository, eventRepository, userRepository,
            importer, zegar, blocks,
            new EventReminderService(participationRepository, notificationService, importer, "3,1"));
        events = new EventService(eventRepository, participationRepository, userRepository, importer,
            matchService, zapisy, performerTagService, location);

        /* Gust Ali: dwoch ulubionych artystow z tagami Last.fm i jeden utwor */
        User ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        ala.getFavoriteArtists().add(artysta("1", "Strachy na Lachy", Set.of("polish rock", "punk rock")));
        ala.getFavoriteArtists().add(artysta("2", "Radiohead", Set.of("alternative", "rock")));
        ala.getFavoriteTracks().add(trackRepository.save(
            new Track("t1", "Tell Me Why", "Jack Savoretti", "99", null)));

        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        ala.addFriend(bob);
        userRepository.save(new User("nowy", "nowy@example.com", "hash"));

        strachy = zapisz("e1", "Strachy na Lachy", LocalDate.of(2026, 10, 20), "Poznan", "Rock", "Pop Rock",
            "Strachy na Lachy");
        MusicEvent strachyDrugi = zapisz("e6", "Strachy na Lachy", LocalDate.of(2026, 10, 21), "Poznan",
            "Rock", "Pop Rock", "Strachy na Lachy");
        strachyDrugi.groupAs(strachy.getSeriesKey());

        zapisz("e2", "Jack Savoretti - Tour", LocalDate.of(2026, 10, 5), "Krakow", "Pop", "Pop", "Jack Savoretti");
        kult = zapisz("e3", "Kult", LocalDate.of(2026, 10, 1), "Warsaw", "Rock", "Punk", "Kult");
        zapisz("e4", "Disco Polo Gala", LocalDate.of(2026, 10, 2), "Lodz", "Pop", "Schlager", "Zespol Weselny");
        jazz = zapisz("e5", "Jazz Night", LocalDate.of(2026, 10, 3), "Krakow", "Jazz", "Jazz", "Trio");
        zapisz("e7", "Candlelight: Tribute to Radiohead", LocalDate.of(2026, 10, 8), "Warsaw",
            "Classical", "Classical", "Candlelight Concerts");
        entityManager.flush();

        /* Bob idzie na jazz - Ala jazzu nie slucha, ale znajomy to tez powod */
        zapisy.participate(jazz.getId(), "bob", ParticipationStatus.GOING, false);
        entityManager.flush();
    }

    private Artist artysta(String id, String nazwa, Set<String> gatunki) {
        Artist a = new Artist(id, nazwa, null);
        a.applyGenres(gatunki);
        return artistRepository.save(a);
    }

    private MusicEvent zapisz(String id, String nazwa, LocalDate data, String miasto,
                              String gatunek, String podgatunek, String... sklad) {
        return eventRepository.save(WydarzeniaTestowe.wydarzenie(id, nazwa, data, miasto, gatunek, podgatunek, sklad));
    }

    private Page<EventCardResponse> dlaCiebie(String kto, String miasto, String fraza) {
        return events.list(EventView.FOR_YOU, miasto, fraza, PageRequest.of(0, 20), kto);
    }

    private static List<String> nazwy(Page<EventCardResponse> page) {
        return page.getContent().stream().map(EventCardResponse::name).toList();
    }

    @Test
    @DisplayName("kolejnosc: ulubiony artysta, artysta w nazwie, utwor, gatunek, znajomi - a niepasujace wcale")
    void ranking() {
        Page<EventCardResponse> lista = dlaCiebie("ala", "", "");

        assertThat(nazwy(lista)).containsExactly(
            "Strachy na Lachy",                    // artysta 100 + gatunek rock 15
            "Candlelight: Tribute to Radiohead",  // artysta w nazwie 100
            "Jack Savoretti - Tour",              // ulubiony utwor 70
            "Kult",                                // gatunek rock 15 + rodzina punk 8
            "Jazz Night");                         // znajomy idzie 20
        // Disco polo z niczym sie nie laczy - nie ma go na liscie
        assertThat(lista.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("kazda pozycja mowi, czemu tu jest")
    void reasons() {
        List<EventCardResponse> lista = dlaCiebie("ala", "", "").getContent();

        assertThat(lista.get(0).reasons()).containsExactly(
            new EventReasonResponse(EventReasonKind.ARTIST, "Strachy na Lachy"),
            new EventReasonResponse(EventReasonKind.GENRE, "rock"));
        assertThat(lista.get(1).reasons()).containsExactly(
            new EventReasonResponse(EventReasonKind.ARTIST, "Radiohead"));
        assertThat(lista.get(2).reasons()).containsExactly(
            new EventReasonResponse(EventReasonKind.TRACK, "Tell Me Why"));
        assertThat(lista.get(4).reasons()).containsExactly(
            new EventReasonResponse(EventReasonKind.FRIENDS, "1"));
        assertThat(lista.get(4).friends()).isEqualTo(1);
    }

    @Test
    @DisplayName("terminy tej samej serii zwiniete, jak na zwyklej liscie")
    void seriesAreGrouped() {
        EventCardResponse pierwsza = dlaCiebie("ala", "", "").getContent().get(0);

        assertThat(pierwsza.id()).isEqualTo(strachy.getId());
        assertThat(pierwsza.moreDates()).isEqualTo(1);
    }

    @Test
    @DisplayName("miasto i fraza dzialaja tak samo jak na liscie po dacie")
    void filters() {
        assertThat(nazwy(dlaCiebie("ala", "Kraków", ""))).containsExactly("Jack Savoretti - Tour", "Jazz Night");
        assertThat(nazwy(dlaCiebie("ala", "", "strachy"))).containsExactly("Strachy na Lachy");
    }

    @Test
    @DisplayName("tagi Last.fm wykonawcy daja dokladniejszy gatunek niz etykieta Ticketmastera")
    void performerTagsSharpenGenres() {
        PerformerTags tagi = new PerformerTags("kult", "Kult");
        tagi.update(Set.of("punk rock", "polish punk"), LocalDateTime.of(2026, 9, 1, 0, 0));
        performerTagsRepository.save(tagi);
        entityManager.flush();

        EventCardResponse karta = dlaCiebie("ala", "", "kult").getContent().get(0);

        // Teraz "punk rock" pasuje dokladnie - wczesniej byla tylko rodzina "punk"
        assertThat(karta.reasons()).contains(new EventReasonResponse(EventReasonKind.GENRE, "punk rock, rock"));
    }

    @Test
    @DisplayName("bez ulubionych i bez znajomych \"Dla ciebie\" jest puste - i info to mowi")
    void emptyProfile() {
        assertThat(dlaCiebie("nowy", "", "").getTotalElements()).isZero();
        assertThat(events.info(false, "nowy").hasTaste()).isFalse();
        assertThat(events.info(false, "ala").hasTaste()).isTrue();
    }

    @Test
    @DisplayName("lista po dacie zostaje po dacie i bez powodow")
    void upcomingIsUnaffected() {
        Page<EventCardResponse> lista = events.list(EventView.UPCOMING, "", "", PageRequest.of(0, 20), "ala");

        assertThat(lista.getContent().get(0).name()).isEqualTo("Kult");
        assertThat(lista.getContent()).allSatisfy(k -> assertThat(k.reasons()).isEmpty());
        assertThat(lista.getTotalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("\"Moje\": to, na co sie zapisalem, z moim stanem")
    void mine() {
        zapisy.participate(kult.getId(), "ala", ParticipationStatus.INTERESTED, false);
        zapisy.participate(strachy.getId(), "ala", ParticipationStatus.GOING, false);
        entityManager.flush();

        List<EventCardResponse> moje = events.list(EventView.MINE, "", "", PageRequest.of(0, 20), "ala").getContent();

        assertThat(moje).extracting(EventCardResponse::name).containsExactly("Kult", "Strachy na Lachy");
        assertThat(moje).extracting(EventCardResponse::myStatus)
            .containsExactly(ParticipationStatus.INTERESTED, ParticipationStatus.GOING);
        assertThat(events.info(false, "ala").mine()).isEqualTo(2);
    }

    @Test
    @DisplayName("strona wydarzenia tez mowi, czemu pasuje - i pokazuje, kto idzie")
    void detailsReasonsAndAttendees() {
        var szczegoly = events.details(jazz.getId(), "ala");

        assertThat(szczegoly.reasons()).containsExactly(new EventReasonResponse(EventReasonKind.FRIENDS, "1"));
        assertThat(szczegoly.attendees()).extracting(a -> a.username()).containsExactly("bob");
        assertThat(szczegoly.attendees().get(0).friend()).isTrue();
        assertThat(szczegoly.participation().going()).isEqualTo(1);
    }

    /* --- Jednostkowo: nazwy i gatunki --- */

    @Test
    @DisplayName("nazwy: wielkosc liter, polskie znaki i \"The\" nie maja znaczenia")
    void nameKeys() {
        assertThat(NameKeys.of("MROZU")).isEqualTo(NameKeys.of("Mrozu"));
        assertThat(NameKeys.of("Dawid Podsiadło")).isEqualTo("dawid podsiadlo");
        assertThat(NameKeys.of("The Weeknd")).isEqualTo(NameKeys.of("Weeknd"));
        assertThat(NameKeys.of("Simon & Garfunkel")).isEqualTo("simon and garfunkel");
    }

    @Test
    @DisplayName("nazwa w tytule: tylko cale slowa i nie dla krotkich nazw")
    void mentions() {
        assertThat(NameKeys.mentions(NameKeys.of("Candlelight: Tribute to Radiohead"), "radiohead")).isTrue();
        assertThat(NameKeys.mentions(NameKeys.of("Radioheadowy wieczor"), "radiohead")).isFalse();
        // "Hey" to zespol, ale "Hey Jude - tribute" to nie jego koncert
        assertThat(NameKeys.mentions(NameKeys.of("Hey Jude - tribute"), "hey")).isFalse();
    }

    @Test
    @DisplayName("gatunki: jedna postac, rozbijanie etykiet Ticketmastera, odsiewanie niegatunkow")
    void genreTags() {
        assertThat(GenreTags.tags("Hip-Hop/Rap")).containsExactly("hiphop", "rap");
        assertThat(GenreTags.normalize("R&B")).isEqualTo("rnb");
        assertThat(GenreTags.normalize("hip hop")).isEqualTo(GenreTags.normalize("Hip-Hop"));
        assertThat(GenreTags.tags("polish")).isEmpty();
        assertThat(GenreTags.tags("Undefined")).isEmpty();
    }

    @Test
    @DisplayName("rodziny gatunkow - takze tam, gdzie krotkie slowo moglo zmylic")
    void genreFamilies() {
        assertThat(GenreTags.families("polishhiphop")).containsExactly("hip-hop");
        assertThat(GenreTags.families("poprock")).containsExactlyInAnyOrder("rock", "pop");
        assertThat(GenreTags.families("dubstep")).containsExactly("electronic");
        assertThat(GenreTags.families("dancehall")).containsExactly("reggae");
        assertThat(GenreTags.families("triphop")).isEmpty();
    }
}
