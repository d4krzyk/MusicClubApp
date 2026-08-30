package com.musicclubapp.entity;

/** Rodzaj kary czasowej nakladanej na konto. */
public enum BanKind {

    /** Nie wolno dodawac ani edytowac postow. */
    POSTING("error.ban.posting"),

    /** Nie wolno pisac na czacie. */
    MESSAGING("error.ban.messaging");

    /** Poczatek klucza komunikatu dla ukaranego. */
    private final String messageKey;

    BanKind(String messageKey) {
        this.messageKey = messageKey;
    }

    /** Klucz komunikatu zaleznie od tego, czy kara ma koniec. */
    public String messageKey(boolean forever) {
        return forever ? messageKey + ".forever" : messageKey;
    }
}
