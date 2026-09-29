package com.musicclubapp.repository;

/** Liczba przypisana do wydarzenia - np. ilu znajomych sie na nie zapisalo. */
public interface EventCountRow {

    Long getEventId();

    long getTotal();
}
