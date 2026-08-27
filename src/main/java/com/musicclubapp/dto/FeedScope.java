package com.musicclubapp.dto;

/**
 * Co ma byc na tablicy.
 *
 * <p><b>Skad sie to wzielo.</b> Postulat brzmial: "najpierw posty znajomych,
 * a obcych albo dopiero potem, albo wcale". To sa dwie rozne odpowiedzi na
 * dwie rozne sytuacje - i wlasnie dlatego jest to <b>przelacznik</b>,
 * a nie decyzja podjeta za uzytkownika raz na zawsze.</p>
 *
 * <p>Domyslnie {@link #ALL}: konto zalozone przed chwila nie ma jeszcze ani
 * jednego znajomego, a aplikacja, ktora wita takiego uzytkownika pusta
 * strona, jest bezuzyteczna dokladnie wtedy, gdy najbardziej potrzebuje go
 * przekonac. Kolejnosc i tak stawia znajomych na gorze, wiec {@link #ALL}
 * nie odbiera niczego temu, kto juz ich ma.</p>
 */
public enum FeedScope {

    /** Znajomi na gorze, pod nimi publiczne posty pozostalych osob. */
    ALL,

    /** Wylacznie moje posty i posty moich znajomych. */
    FRIENDS
}
