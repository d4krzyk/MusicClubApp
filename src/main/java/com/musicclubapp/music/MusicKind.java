package com.musicclubapp.music;

/** Co dokladnie wrzucamy: pojedynczy utwor, caly album czy profil artysty. */
public enum MusicKind {

    /** Pojedynczy utwor - JEDYNY rodzaj, przy ktorym da sie wskazac moment startu. */
    TRACK,

    ALBUM,

    ARTIST,

    /** Playlista - skladanka, ktora ktos ulozyl. */
    PLAYLIST;

    /** Czy przy tym rodzaju ma sens wybieranie momentu, od ktorego zaczyna sie odtwarzanie. */
    public boolean supportsStartSeconds() {
        return this == TRACK;
    }
}
