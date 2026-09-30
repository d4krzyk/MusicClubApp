package com.musicclubapp.entity;

/** Czego dotyczy zgloszenie. */
public enum ReportContext {

    /** Samo konto: nazwa, zdjecie, opis - to, co ktos o sobie pokazuje. */
    PROFILE,

    /** Konkretny post - zgloszenie niesie do niego odnosnik i kopie tresci. */
    POST,

    /** Rozmowa na czacie. */
    CONVERSATION,

    /** Klan: nazwa, skrot, opis i obrazy - to, co klan pokazuje o sobie. */
    CLAN
}
