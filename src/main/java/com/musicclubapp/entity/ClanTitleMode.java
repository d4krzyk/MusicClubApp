package com.musicclubapp.entity;

/** Jak czlonek dostaje tytul w klanie. */
public enum ClanTitleMode {

    /** Nadaje zarzad klanu. */
    MANUAL,

    /** Kazdy czlonek moze sam go wziac i oddac. */
    SELF,

    /** Przychodzi sam, gdy aktywnosc czlonka osiagnie prog. */
    AUTO
}
