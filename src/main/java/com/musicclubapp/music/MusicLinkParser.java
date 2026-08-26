package com.musicclubapp.music;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rozpoznaje adres wklejony przez uzytkownika.
 *
 * <p>Zastapil wczesniejszy {@code SpotifyLink}, ktory umial wylacznie
 * {@code /track/} ze Spotify. <b>To byl prawdziwy blad</b>: kazdy inny adres -
 * album, playlista, podcast, cokolwiek z YouTube'a - byl po cichu POLYKANY.
 * Post powstawal bez odtwarzacza, wpisany moment startu tez znikal, a
 * uzytkownik nie dostawal zadnego komunikatu. Z jego strony wygladalo to
 * dokladnie jak zepsuta aplikacja.</p>
 *
 * <p>Teraz nierozpoznany adres konczy sie <b>bledem walidacji</b> -
 * patrz {@code PoprawnyLinkMuzyczny}.</p>
 *
 * <p>Obslugiwane postacie:</p>
 * <pre>
 * https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT
 * https://open.spotify.com/intl-pl/album/2noRn2Aes5aoNVsU6iWThc?si=abc
 * spotify:artist:4tZwfgrHOc3mvqYlEYSvVi
 * https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=cos
 * https://youtu.be/dQw4w9WgXcQ?t=42
 * https://music.youtube.com/watch?v=dQw4w9WgXcQ
 * </pre>
 *
 * <p>Klasa nie ma stanu, dlatego jest {@code final} z prywatnym konstruktorem.</p>
 */
public final class MusicLinkParser {

    /**
     * Identyfikatory Spotify to 22 znaki Base62.
     * {@code (?:intl-\w+/)?} obsluguje adresy z przedrostkiem jezykowym.
     */
    private static final Pattern SPOTIFY_URL = Pattern.compile(
        "open\\.spotify\\.com/(?:intl-[\\w-]+/)?(track|album|artist)/([A-Za-z0-9]{22})");

    /** Postac "spotify:track:xxx" - kopiowana z aplikacji na komputer. */
    private static final Pattern SPOTIFY_URI = Pattern.compile(
        "spotify:(track|album|artist):([A-Za-z0-9]{22})");

    /**
     * Identyfikator filmu YouTube ma 11 znakow i moze zawierac myslnik
     * oraz podkreslenie - dlatego {@code [\w-]}, a nie sam {@code \w}.
     */
    private static final List<Pattern> YOUTUBE = List.of(
        Pattern.compile("youtube\\.com/watch\\?(?:[^\\s]*&)?v=([\\w-]{11})"),
        Pattern.compile("youtu\\.be/([\\w-]{11})"),
        Pattern.compile("youtube\\.com/embed/([\\w-]{11})"));

    private MusicLinkParser() {
    }

    /**
     * @param adres tekst wklejony przez uzytkownika (moze byc pusty)
     * @return rozpoznany link albo puste {@link Optional}, gdy adres
     *         nie pasuje do zadnego znanego serwisu
     */
    public static Optional<ParsedMusicLink> rozpoznaj(String adres) {
        if (adres == null || adres.isBlank()) {
            return Optional.empty();
        }
        String tekst = adres.trim();

        Optional<ParsedMusicLink> spotify = spotify(tekst);
        if (spotify.isPresent()) {
            return spotify;
        }

        for (Pattern wzorzec : YOUTUBE) {
            Matcher m = wzorzec.matcher(tekst);
            if (m.find()) {
                /*
                 * Film na YouTube to zawsze pojedyncze nagranie. "Album"
                 * bywa tam playlista, a "artysta" kanalem - to inne byty
                 * i nie udajemy, ze umiemy je rozpoznac.
                 */
                return Optional.of(new ParsedMusicLink(
                    MusicProvider.YOUTUBE, MusicKind.TRACK, m.group(1)));
            }
        }

        return Optional.empty();
    }

    private static Optional<ParsedMusicLink> spotify(String tekst) {
        for (Pattern wzorzec : List.of(SPOTIFY_URL, SPOTIFY_URI)) {
            Matcher m = wzorzec.matcher(tekst);
            if (m.find()) {
                return Optional.of(new ParsedMusicLink(
                    MusicProvider.SPOTIFY,
                    /*
                     * Locale.ROOT jest tu KONIECZNE. Domyslne toUpperCase()
                     * uzywa jezyka systemu, a po turecku "artist" zamienia sie
                     * na "ARTIST" z kropka nad I - i valueOf() wywala wyjatek.
                     * Klasyczna pulapka, ktora ujawnia sie tylko u czesci
                     * uzytkownikow.
                     */
                    MusicKind.valueOf(m.group(1).toUpperCase(Locale.ROOT)),
                    m.group(2)));
            }
        }
        return Optional.empty();
    }
}
