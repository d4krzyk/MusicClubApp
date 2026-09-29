package com.musicclubapp.repository;

/** Miasto z liczba nadchodzacych wydarzen - do filtra na liscie. */
public interface EventCityRow {

    String getCityKey();

    /** Nazwa tak, jak podal ja Ticketmaster. */
    String getCityName();

    long getEvents();
}
