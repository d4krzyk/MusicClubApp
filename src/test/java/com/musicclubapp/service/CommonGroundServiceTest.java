package com.musicclubapp.service;

import com.musicclubapp.dto.CommonGroundResponse;
import com.musicclubapp.dto.CatalogArtist;
import com.musicclubapp.dto.CatalogTrack;
import com.musicclubapp.dto.PersonCard;
import com.musicclubapp.entity.Artist;
import com.musicclubapp.entity.Track;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.repository.ArtistRepository;
import com.musicclubapp.repository.TrackRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Co Was laczy" - czesc wspolna dwoch profili.
 *
 * <p>Na prawdziwej bazie, bo gatunki pochodza z osobnej tabeli
 * ({@code artist_genres}) i sa pobierane wlasnym zapytaniem JPQL. Atrapa
 * repozytorium oddalaby to, co jej kazemy, i test przechodzilby takze
 * wtedy, gdyby zapytanie bylo bledne.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Co laczy dwie osoby")
class CommonGroundServiceTest {

    @Autowired private CommonGroundService commonGround;
    @Autowired private UserRepository userRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private EntityManager entityManager;

    private User ala;
    private User bartek;
    private User celina;

    private Artist radiohead;
    private Artist portishead;
    private Artist mozart;

    @BeforeEach
    void setUp() {
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
        bartek = userRepository.save(new User("bartek", "bartek@example.com", "hash"));
        celina = userRepository.save(new User("celina", "celina@example.com", "hash"));

        radiohead = artist("1", "Radiohead", Set.of("rock", "alternative"));
        portishead = artist("2", "Portishead", Set.of("trip-hop", "alternative"));
        mozart = artist("3", "Mozart", Set.of("classical"));

        entityManager.flush();
    }

    private Artist artist(String externalId, String name, Set<String> genres) {
        Artist artist = new Artist(externalId, name, null);
        artist.applyGenres(genres);
        return artistRepository.save(artist);
    }

    private Track track(String externalId, String title, String artistName) {
        return trackRepository.save(new Track(externalId, title, artistName, "9", null));
    }

    @Test
    @DisplayName("wspolny artysta wraca z NAZWA, a nie tylko jako liczba")
    void sharedArtistComesWithItsName() {
        ala.getFavoriteArtists().addAll(Set.of(radiohead, mozart));
        bartek.getFavoriteArtists().addAll(Set.of(radiohead, portishead));
        entityManager.flush();

        CommonGroundResponse wynik = commonGround.between("ala", "bartek");

        /*
         * To jest cala rzecz w tej funkcji. Wczesniej aplikacja umiala
         * powiedziec "1 wspolny artysta" - i na tym sie konczylo.
         */
        assertThat(wynik.artists()).extracting(CatalogArtist::name)
            .containsExactly("Radiohead");
        assertThat(wynik.self()).isFalse();
    }

    @Test
    @DisplayName("artysta, ktorego ma tylko jedna osoba, nie jest wspolny")
    void oneSidedFavouriteIsNotShared() {
        ala.getFavoriteArtists().add(mozart);
        bartek.getFavoriteArtists().add(radiohead);
        entityManager.flush();

        assertThat(commonGround.between("ala", "bartek").artists()).isEmpty();
    }

    @Test
    @DisplayName("wspolne utwory tez wracaja z tytulem i wykonawca")
    void sharedTracks() {
        Track creep = track("10", "Creep", "Radiohead");
        Track glory = track("11", "Glory Box", "Portishead");

        ala.getFavoriteTracks().addAll(Set.of(creep, glory));
        bartek.getFavoriteTracks().add(creep);
        entityManager.flush();

        assertThat(commonGround.between("ala", "bartek").tracks())
            .extracting(CatalogTrack::title)
            .containsExactly("Creep");
    }

    @Test
    @DisplayName("wspolne gatunki sa policzone z ulubionych artystow, bez powtorzen")
    void sharedGenres() {
        // Oboje maja "alternative", ale przez ROZNYCH wykonawcow
        ala.getFavoriteArtists().add(radiohead);      // rock, alternative
        bartek.getFavoriteArtists().add(portishead);  // trip-hop, alternative
        entityManager.flush();

        CommonGroundResponse wynik = commonGround.between("ala", "bartek");

        /*
         * Gatunek jest tu jedynym pomostem: wspolnego wykonawcy nie ma.
         * Wlasnie po to gatunki w ogole sa - zeby dopasowac ludzi, ktorych
         * listy nie pokrywaja sie ani w jednym punkcie.
         */
        assertThat(wynik.genres()).containsExactly("alternative");
        assertThat(wynik.artists()).isEmpty();
    }

    @Test
    @DisplayName("ten sam gatunek u kilku wykonawcow pokazujemy raz")
    void genresAreNotRepeated() {
        Artist inny = artist("4", "Massive Attack", Set.of("trip-hop", "alternative"));

        ala.getFavoriteArtists().addAll(Set.of(radiohead, portishead));
        bartek.getFavoriteArtists().addAll(Set.of(portishead, inny));
        entityManager.flush();

        assertThat(commonGround.between("ala", "bartek").genres())
            .containsExactly("alternative", "trip-hop");
    }

    @Test
    @DisplayName("wspolni znajomi wracaja jako osoby, a nie jako liczba")
    void mutualFriends() {
        ala.addFriend(celina);
        bartek.addFriend(celina);
        userRepository.save(ala);
        userRepository.save(bartek);
        userRepository.save(celina);
        entityManager.flush();

        assertThat(commonGround.between("ala", "bartek").friends())
            .extracting(PersonCard::username)
            .containsExactly("celina");
    }

    @Test
    @DisplayName("wlasny profil nie ma sie z czym porownywac")
    void ownProfileHasNothingToCompare() {
        ala.getFavoriteArtists().add(radiohead);
        entityManager.flush();

        CommonGroundResponse wynik = commonGround.between("ala", "ala");

        /*
         * Bez tego wyjatku wynikiem bylaby cala wlasna lista ulubionych
         * opisana jako "co Was laczy" - technicznie prawda, w interfejsie
         * bez sensu.
         */
        assertThat(wynik.self()).isTrue();
        assertThat(wynik.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("brak czesci wspolnej to pusty wynik, a nie blad")
    void nothingInCommonIsAValidAnswer() {
        CommonGroundResponse wynik = commonGround.between("ala", "bartek");

        assertThat(wynik.isEmpty()).isTrue();
        assertThat(wynik.self()).isFalse();
    }

    @Test
    @DisplayName("nieistniejacy uzytkownik konczy sie bledem 404, a nie pustka")
    void unknownUserIsNotFound() {
        assertThatThrownBy(() -> commonGround.between("ala", "nie-ma-takiego"))
            .isInstanceOf(NoSuchElementFoundException.class);
    }
}
