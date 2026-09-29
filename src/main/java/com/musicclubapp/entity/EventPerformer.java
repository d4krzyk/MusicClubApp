package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Jeden wykonawca w skladzie wydarzenia.
 *
 * Nie laczymy go z {@link Artist}, bo to dwa rozne katalogi: artysci
 * w ulubionych pochodza z Deezera, a sklad koncertu z Ticketmastera, i nie
 * maja wspolnego identyfikatora. Wspolna jest tylko nazwa - i po niej
 * bedziemy ich kiedys dopasowywac.
 */
@Embeddable
public class EventPerformer {

    /** Identyfikator u Ticketmastera. Bywa pusty przy mniejszych wykonawcach. */
    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    protected EventPerformer() {
        // wymagany przez JPA
    }

    public EventPerformer(String externalId, String name) {
        this.externalId = externalId;
        this.name = name;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventPerformer that)) {
            return false;
        }
        return Objects.equals(externalId, that.externalId) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalId, name);
    }
}
