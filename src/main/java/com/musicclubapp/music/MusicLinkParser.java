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
 * https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
 * spotify:artist:4tZwfgrHOc3mvqYlEYSvVi
 *
 * https://www.youtube.com/watch?v=dQw4w9WgXcQ&amp;list=cos
 * https://youtu.be/dQw4w9WgXcQ?t=42
 * https://youtube.com/shorts/dQw4w9WgXcQ
 * https://music.youtube.com/watch?v=dQw4w9WgXcQ
 * https://music.youtube.com/playlist?list=OLAK5uy_abc      (album w YT Music)
 *
 * https://music.apple.com/pl/album/abbey-road/1441164426
 * https://music.apple.com/pl/album/abbey-road/1441164426?i=1441164468
 * https://music.apple.com/pl/playlist/todays-hits/pl.abc123
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
        "open\\.spotify\\.com/(?:intl-[\\w-]+/)?(track|album|artist|playlist)/([A-Za-z0-9]{22})");

    /** Postac "spotify:track:xxx" - kopiowana z aplikacji na komputer. */
    private static final Pattern SPOTIFY_URI = Pattern.compile(
        "spotify:(track|album|artist|playlist):([A-Za-z0-9]{22})");

    /**
     * Identyfikator filmu YouTube ma 11 znakow i moze zawierac myslnik
     * oraz podkreslenie - dlatego {@code [\w-]}, a nie sam {@code \w}.
     */
    private static final List<Pattern> YOUTUBE_UTWOR = List.of(
        Pattern.compile("youtube\\.com/watch\\?(?:[^\\s]*&)?v=([\\w-]{11})"),
        Pattern.compile("youtu\\.be/([\\w-]{11})"),
        Pattern.compile("youtube\\.com/embed/([\\w-]{11})"),
        Pattern.compile("youtube\\.com/shorts/([\\w-]{11})"));

    /**
     * Playlista na YouTube - a w YouTube Music takze KAZDY ALBUM.
     *
     * <p>Udostepniajac album z YouTube Music dostajemy
     * {@code music.youtube.com/playlist?list=OLAK5uy_...} - tam nie ma czegos
     * takiego jak osobny adres albumu. Dlatego album z tego serwisu ladzie
     * u nas jako playlista, a nie dlatego, ze cos pomylilismy.</p>
     *
     * <p>Identyfikatory list maja rozna dlugosc (playlisty uzytkownikow, listy
     * generowane, albumy), wiec zamiast sztywnej liczby znakow przyjmujemy
     * rozsadny zakres.</p>
     */
    private static final Pattern YOUTUBE_PLAYLISTA = Pattern.compile(
        "youtube\\.com/playlist\\?(?:[^\\s]*&)?list=([\\w-]{10,60})");

    /**
     * Apple Music. Zapisujemy CALA SCIEZKE, a nie sam identyfikator.
     *
     * <p>Powod jest praktyczny: adres osadzenia powstaje przez podmiane samej
     * nazwy serwera ({@code music.apple.com} → {@code embed.music.apple.com}),
     * wiec przechowujac sciezke w calosci nie musimy jej pozniej odtwarzac
     * ze skladnikow - a wiec nie mamy jak sie pomylic.</p>
     *
     * <p>Grupa 1 to kraj sklepu (np. {@code pl}), grupa 2 rodzaj, grupa 3
     * reszta sciezki, grupa 4 opcjonalne {@code ?i=} wskazujace konkretny
     * utwor na albumie.</p>
     */
    private static final Pattern APPLE = Pattern.compile(
        "music\\.apple\\.com/([a-z]{2})/(album|playlist|artist|song)/([^?\\s]+)(\\?i=(\\d+))?");

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

        Matcher playlista = YOUTUBE_PLAYLISTA.matcher(tekst);
        if (playlista.find()) {
            return Optional.of(new ParsedMusicLink(
                MusicProvider.YOUTUBE, MusicKind.PLAYLIST, playlista.group(1)));
        }

        for (Pattern wzorzec : YOUTUBE_UTWOR) {
            Matcher m = wzorzec.matcher(tekst);
            if (m.find()) {
                /*
                 * Film to zawsze pojedyncze nagranie. "Artysta" bywa na YouTube
                 * kanalem - to inny byt i nie udajemy, ze umiemy go rozpoznac.
                 */
                return Optional.of(new ParsedMusicLink(
                    MusicProvider.YOUTUBE, MusicKind.TRACK, m.group(1)));
            }
        }

        return apple(tekst);
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

    /**
     * Apple Music. Rodzaj wynika ze sciezki, z jednym wyjatkiem: album
     * z parametrem {@code ?i=} to w rzeczywistosci pojedynczy UTWOR z tego
     * albumu - i tak wlasnie Apple udostepnia piosenki.
     */
    private static Optional<ParsedMusicLink> apple(String tekst) {
        Matcher m = APPLE.matcher(tekst);
        if (!m.find()) {
            return Optional.empty();
        }

        String kraj = m.group(1);
        String typ = m.group(2);
        String reszta = m.group(3);
        String utworNaAlbumie = m.group(5);

        MusicKind kind;
        String sciezka;

        if ("album".equals(typ) && utworNaAlbumie != null) {
            kind = MusicKind.TRACK;
            sciezka = kraj + "/album/" + reszta + "?i=" + utworNaAlbumie;
        } else {
            kind = switch (typ) {
                case "album" -> MusicKind.ALBUM;
                case "playlist" -> MusicKind.PLAYLIST;
                case "artist" -> MusicKind.ARTIST;
                default -> MusicKind.TRACK;      // "song"
            };
            sciezka = kraj + "/" + typ + "/" + reszta;
        }

        return Optional.of(new ParsedMusicLink(MusicProvider.APPLE_MUSIC, kind, sciezka));
    }
}
