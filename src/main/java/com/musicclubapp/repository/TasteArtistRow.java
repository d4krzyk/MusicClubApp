package com.musicclubapp.repository;

/** Wykonawca wspolny dla kilku osob z grupy. */
public interface TasteArtistRow {

    String getExternalId();

    String getName();

    String getImageUrl();

    long getTotal();
}
