package com.musicclubapp.dto;

/** Rodzaj powodu, dla ktorego wydarzenie trafilo do "Dla ciebie". */
public enum EventReasonKind {

    /** Gra ktos z ulubionych artystow. */
    ARTIST,

    /** Gra wykonawca ktoregos z ulubionych utworow. */
    TRACK,

    /** Wspolny gatunek. */
    GENRE,

    /** Zapisali sie znajomi. */
    FRIENDS
}
