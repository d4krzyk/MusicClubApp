package com.musicclubapp.dto;

/** Ktora lista wydarzen: dopasowana do profilu, po kolei w czasie albo moje. */
public enum EventView {

    /** Najlepiej pasujace do ulubionych artystow, utworow i gatunkow - od najlepszego. */
    FOR_YOU,

    /** Wszystkie nadchodzace, od najblizszego. */
    UPCOMING,

    /** Te, na ktore sie zapisalem. */
    MINE
}
