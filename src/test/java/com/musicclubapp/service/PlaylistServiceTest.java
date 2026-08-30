package com.musicclubapp.service;

import com.musicclubapp.dto.PlaylistResponse;
import com.musicclubapp.dto.PlaylistsResponse;
import com.musicclubapp.entity.FavoritePlaylist;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.repository.FavoritePlaylistRepository;
import com.musicclubapp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** Gablotka playlist na profilu. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Gablotka playlist - do pieciu na profil")
class PlaylistServiceTest {

    /** Playlisty Spotify: identyfikator to 22 znaki. */
    private static final String SPOTIFY_1 =
        "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";
    private static final String SPOTIFY_2 =
        "https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd";
    private static final String YT_MUSIC =
        "https://music.youtube.com/playlist?list=OLAK5uy_abcdefghij";
    private static final String SPOTIFY_TRACK =
        "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT";

    @Autowired private PlaylistService playlistService;
    @Autowired private FavoritePlaylistRepository playlistRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @MockBean private MusicMetadataService musicMetadata;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("ala", "ala@example.com", "hash"));
        userRepository.save(new User("bartek", "bartek@example.com", "hash"));

        given(musicMetadata.fetch(any()))
            .willReturn(new MusicMetadataService.Metadata("Skladanka", "https://okladka/x.jpg"));
    }

    @Test
    @DisplayName("wklejony link zamienia sie w pozycje z gotowym odtwarzaczem")
    void addingPlaylistBuildsThePlayer() {
        PlaylistsResponse response = playlistService.add("ala", SPOTIFY_1);

        assertThat(response.items()).hasSize(1);
        PlaylistResponse playlist = response.items().get(0);

        assertThat(playlist.provider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(playlist.title()).isEqualTo("Skladanka");

        /*
         * Adres odtwarzacza sklada SERWER - frontend nie musi wiedziec, ze kazdy serwis robi to
         * inaczej.
         */
        assertThat(playlist.embedUrl())
            .isEqualTo("https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M");
        assertThat(playlist.pageUrl())
            .isEqualTo("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M");
    }

    @Test
    @DisplayName("playlista z YouTube Music tez wchodzi")
    void youTubeMusicWorksToo() {
        PlaylistsResponse response = playlistService.add("ala", YT_MUSIC);

        assertThat(response.items().get(0).embedUrl())
            .isEqualTo("https://www.youtube.com/embed/videoseries?list=OLAK5uy_abcdefghij");
    }

    @Test
    @DisplayName("link do UTWORU jest odrzucany - to gablotka playlist")
    void aTrackIsNotAPlaylist() {
        /* Najczestsza pomylka: adres utworu wyglada bardzo podobnie. */
        assertThatThrownBy(() -> playlistService.add("ala", SPOTIFY_TRACK))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(playlistService.playlists("ala", "ala").items()).isEmpty();
    }

    @Test
    @DisplayName("adres, ktory nie jest linkiem muzycznym, tez odpada")
    void gibberishIsRejected() {
        assertThatThrownBy(() -> playlistService.add("ala", "zupelnie cos innego"))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("tej samej playlisty nie da sie dodac dwa razy")
    void noDuplicates() {
        playlistService.add("ala", SPOTIFY_1);

        assertThatThrownBy(() -> playlistService.add("ala", SPOTIFY_1))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(playlistService.playlists("ala", "ala").items()).hasSize(1);
    }

    @Test
    @DisplayName("ta sama playlista u DWOCH osob jest w porzadku")
    void twoPeopleMayShareTheSamePlaylist() {
        playlistService.add("ala", SPOTIFY_1);
        playlistService.add("bartek", SPOTIFY_1);

        /* Ograniczenie UNIQUE obejmuje pare wlasciciel-playlista, a nie sama playliste. */
        assertThat(playlistService.playlists("ala", "ala").items()).hasSize(1);
        assertThat(playlistService.playlists("bartek", "bartek").items()).hasSize(1);
    }

    @Test
    @DisplayName("szosta playlista sie nie miesci")
    void theShowcaseHoldsFive() {
        for (int i = 0; i < FavoritePlaylist.MAX_PER_USER; i++) {
            playlistService.add("ala", "https://open.spotify.com/playlist/"
                + "37i9dQZF1DXcBWIGoYBM" + (char) ('a' + i) + "x");
        }

        assertThatThrownBy(() -> playlistService.add("ala", SPOTIFY_2))
            .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("usuniecie pozycji ze srodka porzadkuje numery miejsc")
    void removingRenumbers() {
        playlistService.add("ala", SPOTIFY_1);
        playlistService.add("ala", YT_MUSIC);
        playlistService.add("ala", SPOTIFY_2);

        Long fromTheMiddle = playlistService.playlists("ala", "ala").items().get(1).id();
        playlistService.remove("ala", fromTheMiddle);
        entityManager.flush();
        entityManager.clear();

        /*
         * Dziury w numeracji same z siebie nie szkodza - szkodzi to, ze kolejna dodana pozycja
         * dostalaby wtedy numer 3 przy dwoch pozycjach na ekranie.
         */
        List<FavoritePlaylist> left =
            playlistRepository.findByOwnerUsernameOrderByPositionAsc("ala");

        assertThat(left).extracting(FavoritePlaylist::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("cudzej playlisty nie da sie usunac, nawet znajac jej numer")
    void cannotRemoveSomeoneElsesPlaylist() {
        Long alasPlaylist = playlistService.add("ala", SPOTIFY_1).items().get(0).id();

        assertThatThrownBy(() -> playlistService.remove("bartek", alasPlaylist))
            .isInstanceOf(OperationNotAllowedException.class);

        assertThat(playlistService.playlists("ala", "ala").items()).hasSize(1);
    }

    @Test
    @DisplayName("na cudzym profilu gablotka jest tylko do ogladania")
    void someoneElsesShowcaseIsReadOnly() {
        playlistService.add("ala", SPOTIFY_1);

        assertThat(playlistService.playlists("ala", "ala").canEdit()).isTrue();
        assertThat(playlistService.playlists("ala", "bartek").canEdit()).isFalse();
    }

    @Test
    @DisplayName("przy pelnej gablotce nie pytamy serwisu o tytul")
    void fullShowcaseDoesNotCallTheOutsideWorld() {
        for (int i = 0; i < FavoritePlaylist.MAX_PER_USER; i++) {
            playlistService.add("ala", "https://open.spotify.com/playlist/"
                + "37i9dQZF1DXcBWIGoYBM" + (char) ('a' + i) + "x");
        }

        assertThatThrownBy(() -> playlistService.add("ala", SPOTIFY_2))
            .isInstanceOf(OperationNotAllowedException.class);

        /*
         * Piec wywolan, a nie szesc: przy pelnej gablotce sprawdzamy limit PRZED pojsciem do
         * serwisu po tytul nagrania, ktorego i tak nie zapiszemy.
         */
        org.mockito.Mockito.verify(musicMetadata,
            org.mockito.Mockito.times(FavoritePlaylist.MAX_PER_USER)).fetch(any());
    }
}
