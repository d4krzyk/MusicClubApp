package com.musicclubapp.entity;

/**
 * Czy wydarzenie sie odbedzie.
 *
 * Ticketmaster rozroznia tez "onsale" i "offsale" - ale to mowi o sprzedazy
 * biletow, a nie o samym koncercie. Koniec sprzedazy (np. wyprzedane) nie
 * znaczy, ze koncertu nie bedzie, wiec oba laduja w {@link #SCHEDULED}.
 */
public enum EventStatus {

    /** Odbedzie sie zgodnie z planem. */
    SCHEDULED,

    /** Odwolane. */
    CANCELLED,

    /** Przelozone, nowego terminu jeszcze nie ma. */
    POSTPONED,

    /** Przelozone na nowy termin - ten, ktory widac w wydarzeniu. */
    RESCHEDULED
}
