package com.musicclubapp.dto;

/**
 * W jakiej relacji jest ogladajacy z osoba, ktorej profil oglada.
 *
 * <p>Po to, zeby frontend wiedzial, JAKI przycisk narysowac - i zeby nie
 * musial tego zgadywac z trzech osobnych pol typu {@code czyZnajomy},
 * {@code czyWyslalem}, {@code czyDostalem}. Takie flagi mozna ustawic
 * w sprzeczne kombinacje (znajomy ORAZ oczekujace zaproszenie); jeden enum
 * z natury na to nie pozwala.</p>
 *
 * <p>To DTO, a nie encja - w bazie nie ma kolumny "status znajomosci".
 * Ta wartosc jest ZA KAZDYM RAZEM wyliczana z tego, co siedzi
 * w {@code user_friends} i {@code friend_requests}.</p>
 */
public enum FriendshipStatus {

    /** To moj wlasny profil - nie ma czego zapraszac. */
    SELF,

    /** Jestesmy znajomymi. */
    FRIENDS,

    /** Wyslalem zaproszenie i czekam na odpowiedz. */
    REQUEST_SENT,

    /** To ON zaprosil MNIE - moge przyjac albo odrzucic. */
    REQUEST_RECEIVED,

    /** Nic nas nie laczy - moge zaprosic. */
    NONE
}
