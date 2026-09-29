package com.musicclubapp.repository;

import com.musicclubapp.entity.ParticipationStatus;

/** Ile osob zapisalo sie na wydarzenie w danym stanie. */
public interface ParticipationCountRow {

    Long getEventId();

    ParticipationStatus getStatus();

    long getTotal();
}
