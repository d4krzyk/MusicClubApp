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
 * patrz {@code ValidMusicLink}.</p>
 *
 * <p>Obslugiwane postacie:</p>
 * <pre>
 * https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT
 * https://open.spotify.com/intl-pl/album/2noRn2Aes5aoNVsU6iWThc?si=abc
 * https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
 * spotify:artist:4tZwfgrHOc3mvqYlEYSvVi
 *
 * https://music.youtube.com/watch?v=dQw4w9WgXcQ&amp;si=cos
 * https://music.youtube.com/playlist?list=OLAK5uy_abc      (album w YT Music)
 *
 * https://music.apple.com/pl/song/lullaby/1440786034?l=pl
 * https://music.apple.com/pl/album/abbey-road/1441164426
 * https://music.apple.com/pl/album/abbey-road/1441164426?i=1441164468
 * https://music.apple.com/pl/playlist/todays-hits/pl.abc123
 * </pre>
 *
 * <p><b>Zwykly YouTube jest odrzucany</b> - przyjmujemy wylacznie adresy
 * z {@code music.youtube.com}. Dlaczego - patrz {@link #isPlainYouTube}.</p>
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
     * Nagranie w YouTube Music.
     *
     * <p>Identyfikator filmu ma 11 znakow i moze zawierac myslnik oraz
     * podkreslenie - dlatego {@code [\w-]}, a nie sam {@code \w}.</p>
     *
     * <p>Czlon {@code music\.} na poczatku jest <b>obowiazkowy</b>. Bez niego
     * wzorzec lapalby takze zwykly {@code youtube.com/watch}, bo tamten adres
     * konczy sie tym samym tekstem.</p>
     */
    private static final Pattern YT_MUSIC_TRACK = Pattern.compile(
        "music\\.youtube\\.com/watch\\?(?:[^\\s]*&)?v=([\\w-]{11})");

    /**
     * Playlista w YouTube Music - a takze KAZDY ALBUM.
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
    private static final Pattern YT_MUSIC_PLAYLIST = Pattern.compile(
        "music\\.youtube\\.com/playlist\\?(?:[^\\s]*&)?list=([\\w-]{10,60})");

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
    public static Optional<ParsedMusicLink> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String text = url.trim();

        Optional<ParsedMusicLink> spotify = spotify(text);
        if (spotify.isPresent()) {
            return spotify;
        }

        Matcher playlist = YT_MUSIC_PLAYLIST.matcher(text);
        if (playlist.find()) {
            return Optional.of(new ParsedMusicLink(
                MusicProvider.YOUTUBE, MusicKind.PLAYLIST, playlist.group(1)));
        }

        Matcher track = YT_MUSIC_TRACK.matcher(text);
        if (track.find()) {
            /*
             * Nagranie to zawsze pojedynczy utwor. "Artysta" bywa na YouTube
             * kanalem - to inny byt i nie udajemy, ze umiemy go rozpoznac.
             */
            return Optional.of(new ParsedMusicLink(
                MusicProvider.YOUTUBE, MusicKind.TRACK, track.group(1)));
        }

        return apple(text);
    }

    /**
     * Czy to adres ze <b>zwyklego</b> YouTube'a (a wiec taki, ktorego
     * NIE przyjmujemy)?
     *
     * <p><b>Po co osobna metoda, skoro i tak odrzucamy.</b> Zeby powiedziec
     * uzytkownikowi, <i>co</i> zrobil zle. Komunikat "to nie jest link do
     * zadnego znanego serwisu" przy adresie z YouTube'a wyglada jak blad
     * aplikacji - przeciez YouTube jest znany. Dzieki tej metodzie walidator
     * potrafi napisac wprost: "to zwykly YouTube, otworz to w YouTube Music
     * i skopiuj adres stamtad".</p>
     *
     * <p><b>Dlaczego w ogole odrzucamy zwykly YouTube.</b> To decyzja
     * o charakterze aplikacji, nie ograniczenie techniczne: film jest ten sam
     * i ten sam odtwarzacz go pokaze. Chodzi o to, ze na zwyklym YouTube jest
     * <i>wszystko</i> - vlogi, filmiki ze zwierzetami, wykopki - a tablica ma
     * byc o muzyce. {@code music.youtube.com} zawiera wylacznie katalog
     * muzyczny, wiec sam adres jest tu dowodem, ze ktos wrzuca nagranie.</p>
     *
     * <p>Uczciwie: nie jest to filtr szczelny. W YouTube Music trafiaja sie
     * rzeczy, ktore muzyka nie sa, a czesc teledyskow zyje wylacznie na
     * zwyklym YouTube i tych sie nie da wrzucic. To swiadomy kompromis.</p>
     */
    public static boolean isPlainYouTube(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String text = url.toLowerCase(Locale.ROOT);

        /*
         * Kolejnosc ma znaczenie: "music.youtube.com" ZAWIERA "youtube.com",
         * wiec najpierw wykluczamy wersje muzyczna, a dopiero potem pytamy
         * o zwykla. Odwrotnie kazdy poprawny adres z YT Music bylby uznany
         * za blad.
         */
        if (text.contains("music.youtube.com")) {
            return false;
        }
        return text.contains("youtube.com/") || text.contains("youtu.be/");
    }

    private static Optional<ParsedMusicLink> spotify(String text) {
        for (Pattern pattern : List.of(SPOTIFY_URL, SPOTIFY_URI)) {
            Matcher m = pattern.matcher(text);
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
    private static Optional<ParsedMusicLink> apple(String text) {
        Matcher m = APPLE.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }

        String country = m.group(1);
        String type = m.group(2);
        String rest = m.group(3);
        String trackOnAlbum = m.group(5);

        MusicKind kind;
        String path;

        if ("album".equals(type) && trackOnAlbum != null) {
            kind = MusicKind.TRACK;
            path = country + "/album/" + rest + "?i=" + trackOnAlbum;
        } else {
            kind = switch (type) {
                case "album" -> MusicKind.ALBUM;
                case "playlist" -> MusicKind.PLAYLIST;
                case "artist" -> MusicKind.ARTIST;
                default -> MusicKind.TRACK;      // "song"
            };
            path = country + "/" + type + "/" + rest;
        }

        return Optional.of(new ParsedMusicLink(MusicProvider.APPLE_MUSIC, kind, path));
    }
}
