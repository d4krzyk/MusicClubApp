package com.musicclubapp.repository;

/** Liczba przypisana do klanu - np. ilu ma czlonkow albo ile wiadomosci napisano w nim ostatnio. */
public interface ClanCountRow {

    Long getClanId();

    long getTotal();
}
