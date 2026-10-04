package com.musicclubapp.repository;

/** Ulubiony wykonawca danej osoby - do kart w talii (wspolni i pozostali). */
public interface UserArtistRow {

    Long getUserId();

    Long getArtistId();

    String getName();

    String getImageUrl();
}
