package com.musicclubapp.dto;

/** W jakiej relacji jest ogladajacy z osoba, ktorej profil oglada. */
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
