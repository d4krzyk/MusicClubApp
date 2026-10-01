package com.musicclubapp.dto;

/** W jakiej kolejnosci tablica pokazuje posty osob spoza kregu. */
public enum FeedSort {

    /**
     * Znajomi na gorze (od najnowszych), pod nimi posty pozostalych osob wedlug trafnosci: z mojej okolicy,
     * od osob o podobnym guscie i takie, pod ktorymi duzo sie dzieje - z tlumieniem starszych.
     */
    RELEVANT,

    /** Znajomi na gorze, pod nimi pozostali - wszyscy od najnowszych. */
    NEWEST
}
