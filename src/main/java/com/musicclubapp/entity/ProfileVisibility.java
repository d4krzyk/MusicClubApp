package com.musicclubapp.entity;

/** Kto widzi szczegoly profilu: ulubionych, znajomych, posty na profilu, playlisty. */
public enum ProfileVisibility {
    /** Kazdy zalogowany. */
    EVERYONE,
    /** Tylko znajomi - reszta widzi login, awatar i przycisk zaproszenia. */
    FRIENDS
}
