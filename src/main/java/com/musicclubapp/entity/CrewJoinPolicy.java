package com.musicclubapp.entity;

/** Jak wchodzi sie do ekipy. */
public enum CrewJoinPolicy {
    /** Kazdy, kto idzie na koncert, dolacza od razu (do limitu miejsc). */
    OPEN,
    /** Prosba, ktora przyjmuje zakladajacy. */
    APPROVAL
}
