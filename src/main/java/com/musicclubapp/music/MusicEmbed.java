package com.musicclubapp.music;

import java.util.Locale;

/**
 * Sklada adresy z zapisanych w bazie {@code provider + kind + externalId}.
 *
 * <p>Dwa rodzaje adresow:</p>
 * <ul>
 *   <li><b>osadzenia</b> - to, co wchodzi do {@code <iframe>}. Zwykly adres
 *       strony sie nie nada: i Spotify, i YouTube blokuja osadzanie swoich
 *       normalnych stron, do tego sluza osobne adresy z czlonem
 *       {@code /embed/},</li>
 *   <li><b>zwyklego</b> - do wklejenia w formularz edycji i do klikniecia
 *       "otworz w serwisie".</li>
 * </ul>
 *
 * <p>Skladamy je sami, zamiast zapisywac to, co wkleil uzytkownik. Dzieki temu
 * w bazie nie ladują parametry sledzace i ten sam utwor zawsze ma ten sam
 * adres - co jest warunkiem sensownego liczenia "najczesciej wrzucanych".</p>
 */
public final class MusicEmbed {

    private MusicEmbed() {
    }

    /** Adres do wstawienia w {@code <iframe>}. */
    public static String adresOsadzenia(MusicProvider provider, MusicKind kind,
                                        String externalId, Integer startSeconds) {
        return switch (provider) {
            case SPOTIFY -> {
                String adres = "https://open.spotify.com/embed/"
                    + kind.name().toLowerCase(Locale.ROOT) + "/" + externalId;
                // Parametr t= ustawia moment startu i dziala tylko przy utworze
                yield doklejMoment(adres, "?t=", kind, startSeconds);
            }
            case YOUTUBE -> {
                /*
                 * Playlisty maja na YouTube wlasny adres osadzenia: zamiast
                 * identyfikatora filmu podaje sie slowo "videoseries"
                 * i przekazuje liste w parametrze.
                 */
                if (kind == MusicKind.PLAYLIST) {
                    yield "https://www.youtube.com/embed/videoseries?list=" + externalId;
                }
                yield doklejMoment(
                    "https://www.youtube.com/embed/" + externalId, "?start=", kind, startSeconds);
            }
            /*
             * Apple Music: adres osadzenia to ten sam adres z podmieniona
             * nazwa serwera. Dlatego przy tym serwisie trzymamy cala sciezke -
             * nie ma tu czego skladac.
             *
             * Apple nie obsluguje wskazania momentu startu w osadzonym
             * odtwarzaczu, wiec parametru nie doklejamy.
             */
            case APPLE_MUSIC -> "https://embed.music.apple.com/" + externalId;
        };
    }

    /** Adres strony w serwisie - do formularza edycji i do otwarcia w nowej karcie. */
    public static String adresZwykly(MusicProvider provider, MusicKind kind, String externalId) {
        return switch (provider) {
            case SPOTIFY -> "https://open.spotify.com/"
                + kind.name().toLowerCase(Locale.ROOT) + "/" + externalId;
            case YOUTUBE -> kind == MusicKind.PLAYLIST
                ? "https://www.youtube.com/playlist?list=" + externalId
                : "https://www.youtube.com/watch?v=" + externalId;
            case APPLE_MUSIC -> "https://music.apple.com/" + externalId;
        };
    }

    /** Nazwa serwisu do pokazania obok odtwarzacza. */
    public static String nazwaSerwisu(MusicProvider provider) {
        return switch (provider) {
            case SPOTIFY -> "Spotify";
            case YOUTUBE -> "YouTube";
            case APPLE_MUSIC -> "Apple Music";
        };
    }

    /**
     * Adres, pod ktorym serwis oddaje tytul i miniaturke (oEmbed).
     *
     * <p>Oba serwisy wystawiaja go <b>bez zadnego klucza ani tokenu</b> -
     * dlatego dziala dla kazdego uzytkownika, a nie tylko dla piatki
     * wpisanej na liste w panelu Spotify.</p>
     */
    public static String adresOEmbed(MusicProvider provider, MusicKind kind, String externalId) {
        String strona = adresZwykly(provider, kind, externalId);
        // "return null" ponizej oznacza: ten serwis nie ma oEmbed
        return switch (provider) {
            case SPOTIFY -> "https://open.spotify.com/oembed?url=" + strona;
            case YOUTUBE -> "https://www.youtube.com/oembed?format=json&url=" + strona;
            /*
             * Apple Music nie wystawia publicznego oEmbed. Tytul i miniaturka
             * zostana wiec puste - odtwarzacz i tak pokazuje wszystko sam,
             * bo laduje sie w przegladarce niezaleznie od nas.
             */
            case APPLE_MUSIC -> null;
        };
    }

    private static String doklejMoment(String adres, String parametr,
                                       MusicKind kind, Integer startSeconds) {
        boolean warto = kind.obslugujeMomentStartu()
            && startSeconds != null
            && startSeconds > 0;

        return warto ? adres + parametr + startSeconds : adres;
    }
}
