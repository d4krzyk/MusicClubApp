package com.musicclubapp.repository;

/** Jeden wiersz wyniku zapytania o proponowanych znajomych. */
public interface SuggestionRow {

    String getUsername();

    String getAvatarFileName();

    long getSharedFriends();

    long getSharedArtists();

    long getSharedGenres();

    /** Czy ta osoba jest juz naszym znajomym - decyduje o przycisku na karcie. */
    boolean getAlreadyFriend();

    /** Suma punktow dopasowania - ta sama, po ktorej sortuje baza. */
    long getScore();
}
