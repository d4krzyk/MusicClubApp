package com.musicclubapp.repository;

/** Jeden wiersz zestawienia "najczesciej wrzucane": nagranie + ile razy padlo. */
public interface TopMusicRow {

    String getProvider();

    String getKind();

    String getExternalId();

    /** Tytul zapamietany przy dodawaniu posta. */
    String getTitle();

    String getThumbnailUrl();

    long getTimesPosted();
}
