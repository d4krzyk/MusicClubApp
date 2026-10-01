package com.musicclubapp.dto;

/** Jak uporzadkowac przegladarke klanow. */
public enum ClanDirectorySort {

    /** Najbardziej pasujace do gustu ogladajacego (wspolni wykonawcy i gatunki). */
    MATCH,

    /** Najwiecej osob. */
    MEMBERS,

    /** Najnowsze. */
    NEWEST,

    /** Najstarsze. */
    OLDEST,

    /** Najwiecej wiadomosci w ostatnich 7 dniach. */
    ACTIVE,

    /** Alfabetycznie. */
    NAME
}
