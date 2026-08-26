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
            case YOUTUBE -> doklejMoment(
                "https://www.youtube.com/embed/" + externalId, "?start=", kind, startSeconds);
        };
    }

    /** Adres strony w serwisie - do formularza edycji i do otwarcia w nowej karcie. */
    public static String adresZwykly(MusicProvider provider, MusicKind kind, String externalId) {
        return switch (provider) {
            case SPOTIFY -> "https://open.spotify.com/"
                + kind.name().toLowerCase(Locale.ROOT) + "/" + externalId;
            case YOUTUBE -> "https://www.youtube.com/watch?v=" + externalId;
        };
    }

    /** Nazwa serwisu do pokazania obok odtwarzacza. */
    public static String nazwaSerwisu(MusicProvider provider) {
        return switch (provider) {
            case SPOTIFY -> "Spotify";
            case YOUTUBE -> "YouTube";
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
        return switch (provider) {
            case SPOTIFY -> "https://open.spotify.com/oembed?url=" + strona;
            case YOUTUBE -> "https://www.youtube.com/oembed?format=json&url=" + strona;
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
