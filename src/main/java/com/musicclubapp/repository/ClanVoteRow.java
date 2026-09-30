package com.musicclubapp.repository;

/** Ile glosow ma propozycja utworu tygodnia i czy jest wsrod nich glos ogladajacego. */
public interface ClanVoteRow {

    Long getTrackId();

    long getTotal();

    Long getMine();
}
