package com.musicclubapp.entity;

/** Jak ktos zaznaczyl wydarzenie. Brak wiersza = nic nie zaznaczyl albo zrezygnowal. */
public enum ParticipationStatus {

    /** "Zainteresowany" - chce miec wydarzenie na oku. */
    INTERESTED,

    /** "Biore udzial" - idzie. Tylko te osoby sa na liscie uczestnikow. */
    GOING
}
