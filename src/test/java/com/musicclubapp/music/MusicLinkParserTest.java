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
    void utworSpotify() {
        ParsedMusicLink link = MusicLinkParser
            .rozpoznaj("https://open.spotify.com/track/" + ID_SPOTIFY)
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
        assertThat(link.externalId()).isEqualTo(ID_SPOTIFY);
    }

    @Test
    @DisplayName("parametr sledzacy ?si= NIE trafia do bazy")
    void parametrSledzacyOdrzucony() {
        ParsedMusicLink link = MusicLinkParser
            .rozpoznaj("https://open.spotify.com/track/" + ID_SPOTIFY + "?si=tajnyparametr")
            .orElseThrow();

        // Spotify dokleja ?si= przy udostepnianiu i identyfikuje nim osobe udostepniajaca
        assertThat(link.externalId()).isEqualTo(ID_SPOTIFY);
    }

    @Test
    @DisplayName("adres z przedrostkiem jezykowym (intl-pl)")
    void przedrostekJezykowy() {
        ParsedMusicLink link = MusicLinkParser
            .rozpoznaj("https://open.spotify.com/intl-pl/album/" + ID_SPOTIFY)
            .orElseThrow();

        assertThat(link.kind()).isEqualTo(MusicKind.ALBUM);
        assertThat(link.externalId()).isEqualTo(ID_SPOTIFY);
    }

    @Test
    @DisplayName("postac spotify:artist: z aplikacji na komputer")
    void postacUri() {
        ParsedMusicLink link = MusicLinkParser
            .rozpoznaj("spotify:artist:" + ID_SPOTIFY)
            .orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(link.kind()).isEqualTo(MusicKind.ARTIST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.youtube.com/watch?v=" + ID_YOUTUBE,
        "https://www.youtube.com/watch?v=" + ID_YOUTUBE + "&list=PLcos",
        "https://youtu.be/" + ID_YOUTUBE,
        "https://youtu.be/" + ID_YOUTUBE + "?t=42",
        "https://music.youtube.com/watch?v=" + ID_YOUTUBE,
        "https://www.youtube.com/embed/" + ID_YOUTUBE,
    })
    @DisplayName("wszystkie postacie linku do YouTube daja ten sam identyfikator")
    void youtubeWRoznychPostaciach(String adres) {
        ParsedMusicLink link = MusicLinkParser.rozpoznaj(adres).orElseThrow();

        assertThat(link.provider()).isEqualTo(MusicProvider.YOUTUBE);
        // Film to zawsze pojedyncze nagranie - YouTube nie ma "albumu" w naszym sensie
        assertThat(link.kind()).isEqualTo(MusicKind.TRACK);
        assertThat(link.externalId()).isEqualTo(ID_YOUTUBE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "to nie jest zaden link",
        "https://example.com/track/" + ID_SPOTIFY,
        "https://open.spotify.com/track/zakrotki",
        "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
        "https://open.spotify.com/episode/512ojhOuo1ktJprKbVcKyQ",
    })
    @DisplayName("nierozpoznany adres zwraca pusty wynik - i to KONCZY sie bledem walidacji")
    void nierozpoznaneAdresy(String adres) {
        /*
         * Playlisty i podcasty swiadomie NIE sa obslugiwane - aplikacja jest
         * o utworach, albumach i artystach. Wazne jest to, ze taki adres
         * zwraca pusty wynik, a walidator zamienia go na czytelny blad.
         * Wczesniej byl po cichu polykany i post powstawal bez odtwarzacza.
         */
        assertThat(MusicLinkParser.rozpoznaj(adres)).isEmpty();
    }

    @Test
    @DisplayName("pusty i nullowy tekst nie wywalaja sie wyjatkiem")
    void pustyTekst() {
        assertThat(MusicLinkParser.rozpoznaj(null)).isEmpty();
        assertThat(MusicLinkParser.rozpoznaj("")).isEmpty();
        assertThat(MusicLinkParser.rozpoznaj("   ")).isEmpty();
    }

    @Test
    @DisplayName("link wklejony w srodku zdania tez jest rozpoznawany")
    void linkWSrodkuTekstu() {
        Optional<ParsedMusicLink> link = MusicLinkParser.rozpoznaj(
            "posluchajcie https://open.spotify.com/track/" + ID_SPOTIFY + " swietne");

        assertThat(link).isPresent();
        assertThat(link.get().externalId()).isEqualTo(ID_SPOTIFY);
    }
}
