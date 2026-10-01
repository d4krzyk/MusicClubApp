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

    /** Punkty za gust (wykonawcy, znajomi, gatunki) - "dopasowany" znaczy score > 0. */
    long getScore();

    /** Bliskosc miast 0-5 - dolicza sie do kolejnosci, ale nie do "dopasowania". */
    int getNearLevel();

    /** Odleglosc miast w km; 0 = to samo miasto, null = nie wiadomo. */
    Double getDistanceKm();

    /** Miasto - tylko gdy jego wlasciciel je pokazuje i ma jawny profil. */
    String getCity();

    boolean getCityVisible();
}
