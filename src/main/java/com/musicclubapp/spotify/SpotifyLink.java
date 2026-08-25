package com.musicclubapp.spotify;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyciaga identyfikator utworu z adresu wklejonego ze Spotify.
 *
 * <p>Uzytkownik moze wkleic rozne postacie tego samego linku:</p>
 * <pre>
 * https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT
 * https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc123
 * https://open.spotify.com/intl-pl/track/4cOdK2wGLETKBW3PvgPWqT
 * spotify:track:4cOdK2wGLETKBW3PvgPWqT
 * </pre>
 *
 * <p>Z kazdej z nich interesuje nas ten sam kod. Zapisujemy w bazie sam kod,
 * a nie caly adres - dzieki temu nie przechowujemy parametru {@code ?si=},
 * ktory Spotify dokleja do udostepnianych linkow i ktory identyfikuje osobe
 * udostepniajaca.</p>
 *
 * <p>Klasa nie ma stanu i sluzy tylko jako zestaw funkcji pomocniczych,
 * dlatego jest {@code final} z prywatnym konstruktorem.</p>
 */
public final class SpotifyLink {

    /**
     * Identyfikatory Spotify to 22 znaki Base62 (litery i cyfry).
     * {@code (?:intl-\w+/)?} obsluguje adresy z przedrostkiem jezykowym.
     */
    private static final Pattern WZORZEC = Pattern.compile(
        "(?:spotify:track:|open\\.spotify\\.com/(?:intl-[\\w-]+/)?track/)([A-Za-z0-9]{22})");

    private SpotifyLink() {
    }

    /**
     * @param adres tekst wklejony przez uzytkownika (moze byc pusty)
     * @return identyfikator utworu albo puste {@link Optional}, gdy adres
     *         nie wyglada na link do utworu Spotify
     */
    public static Optional<String> wyciagnijIdUtworu(String adres) {
        if (adres == null || adres.isBlank()) {
            return Optional.empty();
        }

        Matcher m = WZORZEC.matcher(adres.trim());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
