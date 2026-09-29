package com.musicclubapp.repository;

/** Jedna karta na liscie wydarzen: najblizszy termin serii i ile ma ich lacznie. */
public interface EventCardRow {

    Long getId();

    long getDatesCount();
}
