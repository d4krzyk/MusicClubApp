package com.musicclubapp.dto;

/** Jak uporzadkowac przegladarke klanow. */
public enum ClanDirectorySort {

    /** Najbardziej pasujace do gustu ogladajacego (wspolni wykonawcy i gatunki) i polozone blisko jego miasta. */
    MATCH,

    /** Od najblizszego miasta ogladajacego; klany bez znanego polozenia na koncu. */
    NEAREST,

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
