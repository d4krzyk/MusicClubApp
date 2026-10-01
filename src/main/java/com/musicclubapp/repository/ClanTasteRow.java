package com.musicclubapp.repository;

/** Gatunek albo wykonawca (identyfikator) wspolny dla {@code total} czlonkow klanu. */
public interface ClanTasteRow {

    Long getClanId();

    String getName();

    long getTotal();
}
