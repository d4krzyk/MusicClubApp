package com.musicclubapp.entity;

/**
 * Czego dotyczy zgloszenie.
 *
 * <p>Rozroznienie jest po to, zeby administrator wiedzial, GDZIE patrzec.
 * Zgloszenie bez tej informacji zmusza go do przeszukania calego konta -
 * postow, rozmowy i profilu naraz - zeby domyslic sie, o co chodzilo.</p>
 */
public enum ReportContext {

    /**
     * Samo konto: nazwa, zdjecie, opis - to, co ktos o sobie pokazuje.
     *
     * <p>Bez dowodow, bo dowodem jest sam profil, ktory administrator
     * moze otworzyc.</p>
     */
    PROFILE,

    /** Konkretny post - zgloszenie niesie do niego odnosnik i kopie tresci. */
    POST,

    /**
     * Rozmowa na czacie.
     *
     * <p>Jedyny przypadek, w ktorym administrator nie moze sam sprawdzic
     * tresci - wiadomosci sa prywatne. Dlatego zgloszenie niesie migawke
     * ostatnich wiadomosci, udostepniona swiadomie przez zglaszajacego.</p>
     */
    CONVERSATION
}
