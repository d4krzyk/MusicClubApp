package com.musicclubapp.entity;

/**
 * Pytania muzyczne na karcie profilu - osoba wybiera do {@link #MAX} i odpowiada wlasnymi slowami. Tresc
 * pytan jest w tlumaczeniach przegladarki; tu tylko nazwy (kolumna z CHECK - wpis w EnumConstraintRefresher).
 */
public enum ProfilePrompt {
    FIRST_CONCERT,
    LIFE_CHANGING_ALBUM,
    DESERT_ISLAND,
    DREAM_GIG,
    GUILTY_PLEASURE,
    ON_REPEAT,
    KARAOKE,
    PARTY_STARTER,
    UNPOPULAR_OPINION,
    INSTRUMENT,
    MORNING_SONG,
    UNDERRATED_ARTIST;

    /** Najwyzej tyle odpowiedzi na jednej karcie. */
    public static final int MAX = 3;
}
