package com.musicclubapp.repository;

import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.User;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import com.musicclubapp.music.ParsedMusicLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy zestawienia "najczesciej wrzucane" na prawdziwej bazie - wymaganie nr 14.
 *
 * <p><b>Skad wzial sie ten test.</b> Zapytanie jest natywne i sortuje po
 * kolumnie WYLICZONEJ ({@code COUNT(*)}), a nie po zwyklym polu. Przy zmianie
 * nazw w projekcie alias tej kolumny zmienil sie w jednym miejscu, a w
 * {@code ORDER BY} zostal stary - i nikt tego nie zauwazyl, bo zadnego testu
 * na to zapytanie nie bylo. Blad wyszedl dopiero w przegladarce, jako 500
 * na profilu. Ten test pilnuje, zeby to sie nie powtorzylo.</p>
 *
 * <p>Sprawdzamy dwie rzeczy naraz: ze zapytanie <b>w ogole sie wykonuje</b>
 * (alias w {@code ORDER BY} musi istniec) i ze <b>kolejnosc jest wlasciwa</b> -
 * od najczesciej wrzucanych.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Najczesciej wrzucane - zapytanie natywne")
class TopMusicRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    private User ala;

    @BeforeEach
    void setUp() {
        ala = userRepository.save(new User("ala", "ala@example.com", "hash"));
    }

    private void post(MusicProvider provider, MusicKind kind, String externalId, String title) {
        Post post = new Post(ala, "cos o muzyce");
        post.applyMusic(new ParsedMusicLink(provider, kind, externalId), null, title, null);
        postRepository.save(post);
    }

    @Test
    @DisplayName("liczy wystapienia i sortuje od najczestszych")
    void countsAndSortsByFrequency() {
        // Trzy razy ten sam utwor, dwa razy inny, raz trzeci
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "aaa", "Pierwszy");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "aaa", "Pierwszy");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "aaa", "Pierwszy");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "bbb", "Drugi");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "bbb", "Drugi");
        post(MusicProvider.YOUTUBE, MusicKind.TRACK, "ccc", "Trzeci");

        List<TopMusicRow> top = postRepository.mostPosted("ala", MusicKind.TRACK.name(), 5);

        assertThat(top).extracting(TopMusicRow::getExternalId)
            .containsExactly("aaa", "bbb", "ccc");
        assertThat(top).extracting(TopMusicRow::getTimesPosted)
            .containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("limit przycina zestawienie")
    void limitTrimsTheList() {
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "aaa", "Pierwszy");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "bbb", "Drugi");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "ccc", "Trzeci");

        assertThat(postRepository.mostPosted("ala", MusicKind.TRACK.name(), 2)).hasSize(2);
    }

    @Test
    @DisplayName("posty bez muzyki i cudze posty nie wchodza do zestawienia")
    void onlyOwnPostsWithMusicCount() {
        User bob = userRepository.save(new User("bob", "bob@example.com", "hash"));
        postRepository.save(new Post(ala, "sam tekst, bez nagrania"));

        Post cudzy = new Post(bob, "cudzy post");
        cudzy.applyMusic(new ParsedMusicLink(MusicProvider.SPOTIFY, MusicKind.TRACK, "zzz"),
            null, "Cudzy", null);
        postRepository.save(cudzy);

        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "aaa", "Moj");

        assertThat(postRepository.mostPosted("ala", MusicKind.TRACK.name(), 5))
            .extracting(TopMusicRow::getExternalId)
            .containsExactly("aaa");
    }

    @Test
    @DisplayName("playlisty nie mieszaja sie do zestawienia utworow")
    void playlistsDoNotMixWithTracks() {
        /*
         * Rodzaj jest parametrem zapytania, wiec playlista wrzucona przez te
         * sama osobe nie ma prawa pojawic sie wsrod utworow. To nie jest
         * drobiazg: playlista to zwykle cudza skladanka i celowo nie liczy sie
         * do statystyk gustu.
         */
        post(MusicProvider.SPOTIFY, MusicKind.PLAYLIST, "lista1", "Skladanka");
        post(MusicProvider.SPOTIFY, MusicKind.TRACK, "aaa", "Utwor");

        assertThat(postRepository.mostPosted("ala", MusicKind.TRACK.name(), 5))
            .extracting(TopMusicRow::getExternalId)
            .containsExactly("aaa");
    }
}
