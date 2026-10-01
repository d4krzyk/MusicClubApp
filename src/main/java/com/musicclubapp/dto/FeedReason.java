package com.musicclubapp.dto;

/** Dlaczego post osoby spoza kregu jest wysoko na tablicy "Dla ciebie". */
public enum FeedReason {

    /** Autor jest z mojego miasta albo z okolicy (do ok. 60 km) i pokazuje swoje miasto. */
    NEAR,

    /** Mamy wspolnych ulubionych wykonawcow albo kilka wspolnych gatunkow. */
    TASTE,

    /** Pod postem sporo sie dzieje: reakcje i komentarze. */
    POPULAR
}
