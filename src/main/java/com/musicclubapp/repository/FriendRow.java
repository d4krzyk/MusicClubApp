package com.musicclubapp.repository;

/** Jeden wiersz listy znajomych: dane osoby + wyliczony wynik powiazania. */
public interface FriendRow {

    String getUsername();

    String getAvatarFileName();

    /** Ilu znajomych ma ta osoba wspolnie z ogladajacym. */
    long getSharedFriends();

    /** Kiedy ta osoba byla ostatnio aktywna; null = nigdy. */
    java.time.LocalDateTime getLastSeenAt();
}
