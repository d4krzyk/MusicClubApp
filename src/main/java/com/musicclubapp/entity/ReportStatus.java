package com.musicclubapp.entity;

/** Co sie stalo ze zgloszeniem. */
public enum ReportStatus {

    /** Czeka na decyzje administratora. */
    OPEN,

    /** Zasadne - administrator podjal dzialanie. */
    RESOLVED,

    /** Bezpodstawne - nic sie nie wydarzylo, zgloszenie zamkniete bez dzialania. */
    DISMISSED
}
