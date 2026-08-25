package com.musicclubapp.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy klasy pomocniczej - wyklad 5 (slajd 8) mowi, ze testy jednostkowe
 * maja obejmowac serwisy ORAZ klasy pomocnicze.
 *
 * <p>To dobry przyklad, po co sie je pisze: uzytkownik moze wkleic link
 * w kilku postaciach, a kazda z nich musi dac ten sam identyfikator.</p>
 */
@DisplayName("SpotifyLink - wyciaganie identyfikatora utworu z linku")
class SpotifyLinkTest {

    private static final String ID = "4cOdK2wGLETKBW3PvgPWqT";

    @Test
    @DisplayName("zwykly link ze Spotify")
    void zwyklyLink() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("https://open.spotify.com/track/" + ID))
            .contains(ID);
    }

    @Test
    @DisplayName("link z parametrem ?si= (tak wyglada 'Udostepnij' w aplikacji)")
    void linkZParametremSi() {
        assertThat(SpotifyLink.wyciagnijIdUtworu(
            "https://open.spotify.com/track/" + ID + "?si=8f3c1d2e4a5b6789"))
            .contains(ID);
    }

    @Test
    @DisplayName("link z przedrostkiem jezykowym /intl-pl/")
    void linkZPrzedrostkiemJezykowym() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("https://open.spotify.com/intl-pl/track/" + ID))
            .contains(ID);
    }

    @Test
    @DisplayName("identyfikator w formacie spotify:track:...")
    void formatUri() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("spotify:track:" + ID)).contains(ID);
    }

    @Test
    @DisplayName("spacje wokol linku nie przeszkadzaja")
    void spacjeSaObcinane() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("  https://open.spotify.com/track/" + ID + "  "))
            .contains(ID);
    }

    @Test
    @DisplayName("link do albumu albo playlisty NIE jest utworem")
    void linkDoAlbumuJestOdrzucany() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("https://open.spotify.com/album/" + ID)).isEmpty();
        assertThat(SpotifyLink.wyciagnijIdUtworu("https://open.spotify.com/playlist/" + ID)).isEmpty();
    }

    @Test
    @DisplayName("zwykly tekst, pusty ciag i null nie wywracaja metody")
    void nieprawidloweDaneDajaPusteOptional() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("jakis tekst")).isEmpty();
        assertThat(SpotifyLink.wyciagnijIdUtworu("")).isEmpty();
        assertThat(SpotifyLink.wyciagnijIdUtworu(null)).isEmpty();
    }

    @Test
    @DisplayName("link z innej domeny jest odrzucany")
    void obcaDomenaJestOdrzucana() {
        assertThat(SpotifyLink.wyciagnijIdUtworu("https://zla-strona.example/track/" + ID)).isEmpty();
    }
}
