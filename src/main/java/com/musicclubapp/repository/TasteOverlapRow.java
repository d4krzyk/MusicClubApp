package com.musicclubapp.repository;

/** Ile gustu laczy ogladajacego z jedna osoba: wspolni ulubieni wykonawcy i wspolne gatunki. */
public interface TasteOverlapRow {

    Long getUserId();

    long getSharedArtists();

    long getSharedGenres();
}
