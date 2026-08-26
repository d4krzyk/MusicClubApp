package com.musicclubapp.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy rozpoznawania wklejanych adresow.
 *
 * <p>Nastepca {@code SpotifyLinkTest}. Doszly tu dwie rzeczy, ktorych stara
 * wersja nie umiala: <b>rodzaj</b> nagrania (utwor / album / artysta)
 * i <b>YouTube</b>.</p>
 */
@DisplayName("MusicLinkParser - rozpoznawanie linkow muzycznych")
class MusicLinkParserTest {

    private static final String ID_SPOTIFY = "4cOdK2wGLETKBW3PvgPWqT";
    private static final String ID_YOUTUBE = "dQw4w9WgXcQ";

    @Test
    @DisplayName("zwykly link do utworu ze Spotify")
    void spotifyTrack() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://open.spotify.com/track/" + ID_SPOTIFY)
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
        assertThat(link.externalId()).isEqualTo(ID_SPOTIFY);
    }

    @Test
    @DisplayName("parametr sledzacy ?si= NIE trafia do bazy")
    void trackingParameterDropped() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://open.spotify.com/track/" + ID_SPOTIFY + "?si=tajnyparametr")
            .orElseThrow();

        // Spotify dokleja ?si= przy udostepnianiu i identyfikuje nim osobe udostepniajaca
        assertThat(link.externalId()).isEqualTo(ID_SPOTIFY);
    }

    @Test
    @DisplayName("adres z przedrostkiem jezykowym (intl-pl)")
    void languagePrefix() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://open.spotify.com/intl-pl/album/" + ID_SPOTIFY)
            .orElseThrow();

        assertThat(link.kind()).isEqualTo(MusicKind.ALBUM);
        assertThat(link.externalId()).isEqualTo(ID_SPOTIFY);
    }

    @Test
    @DisplayName("postac spotify:artist: z aplikacji na komputer")
    void uriForm() {
        ParsedMusicLink link = MusicLinkParser
            .parse("spotify:artist:" + ID_SPOTIFY)
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(link.kind()).isEqualTo(MusicKind.ARTIST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://music.youtube.com/watch?v=" + ID_YOUTUBE,
        "https://music.youtube.com/watch?v=" + ID_YOUTUBE + "&si=abc",
        "https://music.youtube.com/watch?v=" + ID_YOUTUBE + "&list=PLcos",
    })
    @DisplayName("postacie linku z YouTube Music daja ten sam identyfikator")
    void youtubeMusicInVariousForms(String url) {
        ParsedMusicLink link = MusicLinkParser.parse(url).orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.YOUTUBE);
        // Nagranie to zawsze pojedynczy utwor - "artysta" jest tam kanalem
        assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
        assertThat(link.externalId()).isEqualTo(ID_YOUTUBE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.youtube.com/watch?v=" + ID_YOUTUBE,
        "https://youtu.be/" + ID_YOUTUBE,
        "https://youtu.be/" + ID_YOUTUBE + "?t=42",
        "https://www.youtube.com/embed/" + ID_YOUTUBE,
        "https://youtube.com/shorts/" + ID_YOUTUBE,
        "https://www.youtube.com/playlist?list=OLAK5uy_abcdefghij",
    })
    @DisplayName("ZWYKLY YouTube jest odrzucany - przyjmujemy tylko YouTube Music")
    void plainYouTubeRejected(String url) {
        /*
         * To jest decyzja o charakterze aplikacji, nie ograniczenie techniczne:
         * film jest ten sam. Na zwyklym YouTube jest jednak wszystko - vlogi,
         * filmiki, wykopki - a tablica ma byc o muzyce.
         */
        assertThat(MusicLinkParser.parse(url)).isEmpty();
        // ...ale rozpoznajemy, ZE to YouTube, zeby dac trafniejszy komunikat
        assertThat(MusicLinkParser.isPlainYouTube(url)).isTrue();
    }

    @Test
    @DisplayName("adres z YouTube Music NIE jest uznany za zwykly YouTube")
    void musicIsNotPlainYouTube() {
        /*
         * Pulapka warta testu: tekst "music.youtube.com" ZAWIERA "youtube.com".
         * Gdyby sprawdzac to w zlej kolejnosci, kazdy poprawny adres z YT Music
         * dostawalby komunikat "to zwykly YouTube".
         */
        assertThat(MusicLinkParser.isPlainYouTube(
            "https://music.youtube.com/watch?v=" + ID_YOUTUBE)).isFalse();
        assertThat(MusicLinkParser.isPlainYouTube(
            "https://music.youtube.com/playlist?list=OLAK5uy_abcdefghij")).isFalse();
        assertThat(MusicLinkParser.isPlainYouTube(
            "https://open.spotify.com/track/" + ID_SPOTIFY)).isFalse();
        assertThat(MusicLinkParser.isPlainYouTube(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "to nie jest zaden link",
        "https://example.com/track/" + ID_SPOTIFY,
        "https://open.spotify.com/track/zakrotki",
        "https://open.spotify.com/episode/512ojhOuo1ktJprKbVcKyQ",
    })
    @DisplayName("nierozpoznany adres zwraca pusty wynik - i to KONCZY sie bledem walidacji")
    void unrecognizedUrls(String url) {
        /*
         * Podcasty swiadomie NIE sa obslugiwane - aplikacja jest o muzyce.
         * Wazne jest to, ze taki adres zwraca pusty wynik, a walidator
         * zamienia go na czytelny blad. Wczesniej byl po cichu polykany
         * i post powstawal bez odtwarzacza.
         */
        assertThat(MusicLinkParser.parse(url)).isEmpty();
    }

    @Test
    @DisplayName("playlista ze Spotify")
    void spotifyPlaylist() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
            .orElseThrow();

        assertThat(link.kind()).isEqualTo(MusicKind.PLAYLIST);
        assertThat(link.externalId()).isEqualTo("37i9dQZF1DXcBWIGoYBM5M");
    }

    @Test
    @DisplayName("ALBUM z YouTube Music przychodzi jako playlista - i tak ma byc")
    void youtubeMusicAlbumIsPlaylist() {
        /*
         * W YouTube Music nie ma osobnego adresu albumu - udostepniajac album
         * dostajemy adres playlisty (OLAK5uy_...). To nie jest nasza pomylka,
         * tylko sposob dzialania tamtego serwisu.
         */
        ParsedMusicLink link = MusicLinkParser
            .parse("https://music.youtube.com/playlist?list=OLAK5uy_abcdefghij")
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.YOUTUBE);
        assertThat(link.kind()).isEqualTo(MusicKind.PLAYLIST);
        assertThat(link.externalId()).isEqualTo("OLAK5uy_abcdefghij");
    }

    @Test
    @DisplayName("Apple Music: album")
    void appleAlbum() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://music.apple.com/pl/album/abbey-road/1441164426")
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.APPLE_MUSIC);
        assertThat(link.kind()).isEqualTo(MusicKind.ALBUM);
        // Przy Apple zapisujemy CALA sciezke - adres osadzenia powstaje
        // przez sama podmiane nazwy serwera
        assertThat(link.externalId()).isEqualTo("pl/album/abbey-road/1441164426");
    }

    @Test
    @DisplayName("Apple Music: album z ?i= to POJEDYNCZY UTWOR, nie album")
    void appleTrackOnAlbum() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://music.apple.com/pl/album/abbey-road/1441164426?i=1441164468")
            .orElseThrow();

        assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
        assertThat(link.externalId()).isEqualTo("pl/album/abbey-road/1441164426?i=1441164468");
    }

    @Test
    @DisplayName("Apple Music: adres /song/ - i parametr ?l= nie trafia do bazy")
    void appleTrack() {
        ParsedMusicLink link = MusicLinkParser
            .parse("https://music.apple.com/pl/song/lullaby/1440786034?l=pl")
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.APPLE_MUSIC);
        assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
        // ?l=pl to tylko jezyk interfejsu Apple - do adresu osadzenia niepotrzebny
        assertThat(link.externalId()).isEqualTo("pl/song/lullaby/1440786034");
    }

    @Test
    @DisplayName("Apple Music: playlista i artysta")
    void applePlaylistAndArtist() {
        assertThat(MusicLinkParser
            .parse("https://music.apple.com/us/playlist/todays-hits/pl.abc123")
            .orElseThrow().kind()).isEqualTo(MusicKind.PLAYLIST);

        assertThat(MusicLinkParser
            .parse("https://music.apple.com/us/artist/the-beatles/136975")
            .orElseThrow().kind()).isEqualTo(MusicKind.ARTIST);
    }

    @Test
    @DisplayName("pusty i nullowy tekst nie wywalaja sie wyjatkiem")
    void emptyText() {
        assertThat(MusicLinkParser.parse(null)).isEmpty();
        assertThat(MusicLinkParser.parse("")).isEmpty();
        assertThat(MusicLinkParser.parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("link wklejony w srodku zdania tez jest rozpoznawany")
    void linkInsideText() {
        Optional<ParsedMusicLink> link = MusicLinkParser.parse(
            "posluchajcie https://open.spotify.com/track/" + ID_SPOTIFY + " swietne");

        assertThat(link).isPresent();
        assertThat(link.get().externalId()).isEqualTo(ID_SPOTIFY);
    }
}
