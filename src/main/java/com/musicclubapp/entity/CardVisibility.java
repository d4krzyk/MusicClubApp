package com.musicclubapp.entity;

/**
 * Kto widzi moja karte (zdjecia, "o mnie", "szukam", pytania) na profilu. W talii Poznawaj karta jest zawsze -
 * o tym decyduje sam tryb Poznawaj, nie to ustawienie.
 */
public enum CardVisibility {
    /** Kazdy, kto widzi szczegoly profilu (przy profilu "tylko znajomi" - i tak tylko znajomi). */
    EVERYONE,
    /** Tylko znajomi - takze przy profilu dla wszystkich. */
    FRIENDS,
    /** Na profilu nikt poza mna (i administratorem); karta jest tylko w trybie Poznawaj. */
    DISCOVER_ONLY
}
