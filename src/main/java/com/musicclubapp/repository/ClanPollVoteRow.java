package com.musicclubapp.repository;

/** Ile glosow ma odpowiedz w ankiecie i czy jest wsrod nich glos ogladajacego. */
public interface ClanPollVoteRow {

    Long getOptionId();

    long getTotal();

    Long getMine();
}
