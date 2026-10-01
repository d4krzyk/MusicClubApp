package com.musicclubapp.entity;

/** Jak do klanu mozna sie dostac poza zaproszeniem od czlonka. */
public enum ClanJoinPolicy {

    /** Klan przyjmuje prosby o dolaczenie - zarzad je przyjmuje albo odrzuca. */
    REQUESTS,

    /** Tylko zaproszenia od czlonkow (tak jak wczesniej). */
    INVITE_ONLY
}
