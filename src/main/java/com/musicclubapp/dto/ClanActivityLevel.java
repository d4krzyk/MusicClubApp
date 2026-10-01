package com.musicclubapp.dto;

/** Jak ozywiony jest czat klanu - z liczby wiadomosci z ostatnich 7 dni, bez ujawniania ich tresci. */
public enum ClanActivityLevel {
    NONE, LOW, MEDIUM, HIGH;

    /** Progi: 1-19 spokojny, 20-99 aktywny, 100 i wiecej bardzo aktywny. */
    public static ClanActivityLevel of(long messagesLastWeek) {
        if (messagesLastWeek <= 0) {
            return NONE;
        }
        if (messagesLastWeek < 20) {
            return LOW;
        }
        return messagesLastWeek < 100 ? MEDIUM : HIGH;
    }
}
