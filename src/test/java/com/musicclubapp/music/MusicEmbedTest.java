package com.musicclubapp.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy skladania adresow osadzenia.
 *
 * <p>Najwazniejsze jest tu pilnowanie, ze <b>moment startu dokleja sie
 * WYLACZNIE przy utworze</b> - przy albumie i artyscie nie ma czego
 * przewijac, a doklejony parametr tylko zasmiecalby adres.</p>
 */
@DisplayName("MusicEmbed - adresy odtwarzaczy")
class MusicEmbedTest {

    private static final String ID_SPOTIFY = "4cOdK2wGLETKBW3PvgPWqT";
    private static final String ID_YOUTUBE = "dQw4w9WgXcQ";

    @Test
    @DisplayName("utwor ze Spotify z momentem startu")
    void spotifyUtworZMomentem() {
        String adres = MusicEmbed.adresOsadzenia(
            MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY, 70);

        assertThat(adres).isEqualTo(
            "https://open.spotify.com/embed/track/" + ID_SPOTIFY + "?t=70");
    }

    @Test
    @DisplayName("album ze Spotify - BEZ momentu, nawet gdy ktos go przysle")
    void albumIgnorujeMoment() {
        String adres = MusicEmbed.adresOsadzenia(
            MusicProvider.SPOTIFY, MusicKind.ALBUM, ID_SPOTIFY, 70);

        assertThat(adres).isEqualTo("https://open.spotify.com/embed/album/" + ID_SPOTIFY);
        assertThat(adres).doesNotContain("t=");
    }

    @Test
    @DisplayName("artysta ze Spotify - tez bez momentu")
    void artystaIgnorujeMoment() {
        String adres = MusicEmbed.adresOsadzenia(
            MusicProvider.SPOTIFY, MusicKind.ARTIST, ID_SPOTIFY, 30);

        assertThat(adres).isEqualTo("https://open.spotify.com/embed/artist/" + ID_SPOTIFY);
    }

    @Test
    @DisplayName("YouTube uzywa parametru start=, a nie t=")
    void youtubeMaSwojParametr() {
        String adres = MusicEmbed.adresOsadzenia(
            MusicProvider.YOUTUBE, MusicKind.TRACK, ID_YOUTUBE, 42);

        assertThat(adres).isEqualTo("https://www.youtube.com/embed/" + ID_YOUTUBE + "?start=42");
    }

    @Test
    @DisplayName("moment 0 i pusty nie dokladaja parametru")
    void zeroNieDoklejaParametru() {
        assertThat(MusicEmbed.adresOsadzenia(
            MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY, 0)).doesNotContain("t=");
        assertThat(MusicEmbed.adresOsadzenia(
            MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY, null)).doesNotContain("t=");
    }

    @Test
    @DisplayName("adres zwykly prowadzi na strone serwisu")
    void adresZwykly() {
        assertThat(MusicEmbed.adresZwykly(MusicProvider.SPOTIFY, MusicKind.ALBUM, ID_SPOTIFY))
            .isEqualTo("https://open.spotify.com/album/" + ID_SPOTIFY);
        assertThat(MusicEmbed.adresZwykly(MusicProvider.YOUTUBE, MusicKind.TRACK, ID_YOUTUBE))
            .isEqualTo("https://www.youtube.com/watch?v=" + ID_YOUTUBE);
    }

    @Test
    @DisplayName("adres oEmbed jest zbudowany z adresu strony")
    void adresOEmbed() {
        assertThat(MusicEmbed.adresOEmbed(MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY))
            .isEqualTo("https://open.spotify.com/oembed?url=https://open.spotify.com/track/"
                + ID_SPOTIFY);
        assertThat(MusicEmbed.adresOEmbed(MusicProvider.YOUTUBE, MusicKind.TRACK, ID_YOUTUBE))
            .contains("youtube.com/oembed").contains("format=json");
    }

    @Test
    @DisplayName("tylko utwor obsluguje moment startu")
    void tylkoUtworMaMoment() {
        assertThat(MusicKind.TRACK.obslugujeMomentStartu()).isTrue();
        assertThat(MusicKind.ALBUM.obslugujeMomentStartu()).isFalse();
        assertThat(MusicKind.ARTIST.obslugujeMomentStartu()).isFalse();
    }
}
