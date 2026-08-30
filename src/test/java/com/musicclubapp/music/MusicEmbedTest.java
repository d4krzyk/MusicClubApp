package com.musicclubapp.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Testy skladania adresow osadzenia. */
@DisplayName("MusicEmbed - adresy odtwarzaczy")
class MusicEmbedTest {

    private static final String ID_SPOTIFY = "4cOdK2wGLETKBW3PvgPWqT";
    private static final String ID_YOUTUBE = "dQw4w9WgXcQ";

    @Test
    @DisplayName("utwor ze Spotify z momentem startu")
    void spotifyTrackWithStartSeconds() {
        String url = MusicEmbed.embedUrl(
            MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY, 70);

        assertThat(url).isEqualTo(
            "https://open.spotify.com/embed/track/" + ID_SPOTIFY + "?t=70");
    }

    @Test
    @DisplayName("album ze Spotify - BEZ momentu, nawet gdy ktos go przysle")
    void albumIgnoresStartSeconds() {
        String url = MusicEmbed.embedUrl(
            MusicProvider.SPOTIFY, MusicKind.ALBUM, ID_SPOTIFY, 70);

        assertThat(url).isEqualTo("https://open.spotify.com/embed/album/" + ID_SPOTIFY);
        assertThat(url).doesNotContain("t=");
    }

    @Test
    @DisplayName("artysta ze Spotify - tez bez momentu")
    void artistIgnoresStartSeconds() {
        String url = MusicEmbed.embedUrl(
            MusicProvider.SPOTIFY, MusicKind.ARTIST, ID_SPOTIFY, 30);

        assertThat(url).isEqualTo("https://open.spotify.com/embed/artist/" + ID_SPOTIFY);
    }

    @Test
    @DisplayName("YouTube uzywa parametru start=, a nie t=")
    void youtubeUsesItsOwnParameter() {
        String url = MusicEmbed.embedUrl(
            MusicProvider.YOUTUBE, MusicKind.TRACK, ID_YOUTUBE, 42);

        assertThat(url).isEqualTo("https://www.youtube.com/embed/" + ID_YOUTUBE + "?start=42");
    }

    @Test
    @DisplayName("moment 0 i pusty nie dokladaja parametru")
    void zeroAddsNoParameter() {
        assertThat(MusicEmbed.embedUrl(
            MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY, 0)).doesNotContain("t=");
        assertThat(MusicEmbed.embedUrl(
            MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY, null)).doesNotContain("t=");
    }

    @Test
    @DisplayName("adres zwykly prowadzi na strone serwisu")
    void canonicalUrl() {
        assertThat(MusicEmbed.canonicalUrl(MusicProvider.SPOTIFY, MusicKind.ALBUM, ID_SPOTIFY))
            .isEqualTo("https://open.spotify.com/album/" + ID_SPOTIFY);
    }

    @Test
    @DisplayName("adres zwykly dla YouTube prowadzi do YouTube MUSIC")
    void canonicalUrlIsYouTubeMusic() {
        /* To NIE jest kosmetyka. */
        assertThat(MusicEmbed.canonicalUrl(MusicProvider.YOUTUBE, MusicKind.TRACK, ID_YOUTUBE))
            .isEqualTo("https://music.youtube.com/watch?v=" + ID_YOUTUBE);
        assertThat(MusicEmbed.canonicalUrl(MusicProvider.YOUTUBE, MusicKind.PLAYLIST, "OLAK5uy_abc"))
            .isEqualTo("https://music.youtube.com/playlist?list=OLAK5uy_abc");
    }

    @Test
    @DisplayName("adres oEmbed jest zbudowany z adresu strony")
    void oEmbedUrl() {
        assertThat(MusicEmbed.oEmbedUrl(MusicProvider.SPOTIFY, MusicKind.TRACK, ID_SPOTIFY))
            .isEqualTo("https://open.spotify.com/oembed?url=https://open.spotify.com/track/"
                + ID_SPOTIFY);
    }

    @Test
    @DisplayName("oEmbed pyta GLOWNY YouTube, mimo ze pokazujemy YouTube Music")
    void oEmbedGoesToMainYouTube() {
        // Uslugi oEmbed nie wystawia music.youtube.com - film jest ten sam,
        // wiec tytul i miniaturka wychodza poprawne
        assertThat(MusicEmbed.oEmbedUrl(MusicProvider.YOUTUBE, MusicKind.TRACK, ID_YOUTUBE))
            .isEqualTo("https://www.youtube.com/oembed?format=json"
                + "&url=https://www.youtube.com/watch?v=" + ID_YOUTUBE);
    }

    @Test
    @DisplayName("Apple: tytul odczytany z adresu, bo oEmbed tam nie ma")
    void appleTitleFromUrl() {
        assertThat(MusicEmbed.titleFromUrl(MusicProvider.APPLE_MUSIC, "pl/song/lullaby/1440786034"))
            .isEqualTo("Lullaby");
        assertThat(MusicEmbed.titleFromUrl(
            MusicProvider.APPLE_MUSIC, "pl/album/abbey-road/1441164426"))
            .isEqualTo("Abbey Road");

        // Polskie znaki sa w adresie zakodowane - rozkodowujemy je
        assertThat(MusicEmbed.titleFromUrl(
            MusicProvider.APPLE_MUSIC, "pl/album/tak-mi-%C5%BCal/999"))
            .isEqualTo("Tak Mi Żal");
    }

    @Test
    @DisplayName("Apple: przy ?i= NIE zgadujemy tytulu - w adresie jest nazwa ALBUMU")
    void appleTrackOnAlbumHasNoTitle() {
        /* "pl/album/abbey-road/...?i=..." to konkretna piosenka z tej plyty. */
        assertThat(MusicEmbed.titleFromUrl(
            MusicProvider.APPLE_MUSIC, "pl/album/abbey-road/1441164426?i=1441164468")).isNull();
    }

    @Test
    @DisplayName("Spotify i YouTube maja oEmbed, wiec tytulu z adresu nie czytamy")
    void titleFromUrlOnlyForApple() {
        assertThat(MusicEmbed.titleFromUrl(MusicProvider.SPOTIFY, ID_SPOTIFY)).isNull();
        assertThat(MusicEmbed.titleFromUrl(MusicProvider.YOUTUBE, ID_YOUTUBE)).isNull();
        assertThat(MusicEmbed.titleFromUrl(MusicProvider.APPLE_MUSIC, null)).isNull();
    }

    @Test
    @DisplayName("playlista YouTube ma adres z 'videoseries'")
    void youtubePlaylist() {
        String url = MusicEmbed.embedUrl(
            MusicProvider.YOUTUBE, MusicKind.PLAYLIST, "OLAK5uy_abc", null);

        assertThat(url)
            .isEqualTo("https://www.youtube.com/embed/videoseries?list=OLAK5uy_abc");
    }

    @Test
    @DisplayName("Apple Music: adres osadzenia to podmiana samej nazwy serwera")
    void appleEmbed() {
        String path = "pl/album/abbey-road/1441164426?i=1441164468";

        assertThat(MusicEmbed.embedUrl(
            MusicProvider.APPLE_MUSIC, MusicKind.TRACK, path, 70))
            .isEqualTo("https://embed.music.apple.com/" + path);

        assertThat(MusicEmbed.canonicalUrl(MusicProvider.APPLE_MUSIC, MusicKind.TRACK, path))
            .isEqualTo("https://music.apple.com/" + path);
    }

    @Test
    @DisplayName("Apple Music nie ma oEmbed - zwracamy null zamiast zmyslac adres")
    void appleHasNoOEmbed() {
        assertThat(MusicEmbed.oEmbedUrl(
            MusicProvider.APPLE_MUSIC, MusicKind.ALBUM, "pl/album/x/1")).isNull();
    }

    @Test
    @DisplayName("playlista NIE liczy sie do statystyk gustu")
    void playlistHasNoStartSeconds() {
        // Playlista to nie konkretne nagranie - moment startu nie ma sensu
        assertThat(MusicKind.PLAYLIST.supportsStartSeconds()).isFalse();
    }

    @Test
    @DisplayName("tylko utwor obsluguje moment startu")
    void onlyTrackHasStartSeconds() {
        assertThat(MusicKind.TRACK.supportsStartSeconds()).isTrue();
        assertThat(MusicKind.ALBUM.supportsStartSeconds()).isFalse();
        assertThat(MusicKind.ARTIST.supportsStartSeconds()).isFalse();
    }
}
