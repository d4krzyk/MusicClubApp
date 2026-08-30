package com.musicclubapp.music;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Sklada adresy z zapisanych w bazie provider + kind + externalId. */
public final class MusicEmbed {

    private MusicEmbed() {
    }

    /** Adres do wstawienia w {@code <iframe>}. */
    public static String embedUrl(MusicProvider provider, MusicKind kind,
                                        String externalId, Integer startSeconds) {
        return switch (provider) {
            case SPOTIFY -> {
                String url = "https://open.spotify.com/embed/"
                    + kind.name().toLowerCase(Locale.ROOT) + "/" + externalId;
                // Parametr t= ustawia moment startu i dziala tylko przy utworze
                yield appendStartSeconds(url, "?t=", kind, startSeconds);
            }
            case YOUTUBE -> {
                /*
                 * Playlisty maja na YouTube wlasny adres osadzenia: zamiast identyfikatora filmu
                 * podaje sie slowo "videoseries" i przekazuje liste w parametrze.
                 */
                if (kind == MusicKind.PLAYLIST) {
                    yield "https://www.youtube.com/embed/videoseries?list=" + externalId;
                }
                yield appendStartSeconds(
                    "https://www.youtube.com/embed/" + externalId, "?start=", kind, startSeconds);
            }
            /* Apple Music: adres osadzenia to ten sam adres z podmieniona nazwa serwera. */
            case APPLE_MUSIC -> "https://embed.music.apple.com/" + externalId;
        };
    }

    /** Adres strony w serwisie - do formularza edycji i do otwarcia w nowej karcie. */
    public static String canonicalUrl(MusicProvider provider, MusicKind kind, String externalId) {
        return switch (provider) {
            case SPOTIFY -> "https://open.spotify.com/"
                + kind.name().toLowerCase(Locale.ROOT) + "/" + externalId;
            /* Swiadomie music.youtube.com, a nie www.youtube.com. */
            case YOUTUBE -> kind == MusicKind.PLAYLIST
                ? "https://music.youtube.com/playlist?list=" + externalId
                : "https://music.youtube.com/watch?v=" + externalId;
            case APPLE_MUSIC -> "https://music.apple.com/" + externalId;
        };
    }

    /** Nazwa serwisu do pokazania obok odtwarzacza. */
    public static String providerName(MusicProvider provider) {
        return switch (provider) {
            case SPOTIFY -> "Spotify";
            case YOUTUBE -> "YouTube Music";
            case APPLE_MUSIC -> "Apple Music";
        };
    }

    /** Adres, pod ktorym serwis oddaje tytul i miniaturke (oEmbed). */
    public static String oEmbedUrl(MusicProvider provider, MusicKind kind, String externalId) {
        // "return null" ponizej oznacza: ten serwis nie ma oEmbed
        return switch (provider) {
            case SPOTIFY -> "https://open.spotify.com/oembed?url="
                + canonicalUrl(provider, kind, externalId);
            /* Tu MUSI byc www.youtube.com, choc uzytkownikowi pokazujemy music.youtube.com. */
            case YOUTUBE -> "https://www.youtube.com/oembed?format=json&url="
                + (kind == MusicKind.PLAYLIST
                    ? "https://www.youtube.com/playlist?list=" + externalId
                    : "https://www.youtube.com/watch?v=" + externalId);
            /* Apple Music nie wystawia publicznego oEmbed. */
            case APPLE_MUSIC -> null;
        };
    }

    /** Tytul odczytany z samego adresu - awaryjnie, gdy serwis nie ma oEmbed. */
    public static String titleFromUrl(MusicProvider provider, String externalId) {
        if (provider != MusicProvider.APPLE_MUSIC || externalId == null) {
            // Spotify i YouTube maja oEmbed, wiec podaja prawdziwy tytul
            return null;
        }

        // Utwor wskazany przez ?i= - w adresie jest nazwa albumu, nie utworu
        if (externalId.contains("?i=")) {
            return null;
        }

        // pl/song/lullaby/1440786034  ->  czesci[2] to nazwa
        String[] parts = externalId.split("/");
        if (parts.length < 3) {
            return null;
        }

        String name = URLDecoder.decode(parts[2], StandardCharsets.UTF_8)
            .replace('-', ' ')
            .trim();

        return name.isEmpty() ? null : capitalizeWords(name);
    }

    private static String capitalizeWords(String text) {
        return Arrays.stream(text.split(" "))
            .filter(word -> !word.isEmpty())
            .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    private static String appendStartSeconds(String url, String parameter,
                                       MusicKind kind, Integer startSeconds) {
        boolean worthKeeping = kind.supportsStartSeconds()
            && startSeconds != null
            && startSeconds > 0;

        return worthKeeping ? url + parameter + startSeconds : url;
    }
}
