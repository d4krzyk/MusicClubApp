package com.musicclubapp.dto;

/** Wynik klikniecia w link: co sie stalo i czego jeszcze brakuje. */
public record EmailVerificationResponse(Result result, String username, String email) {

    public enum Result {
        /** Adres nowego konta potwierdzony. */
        VERIFIED,
        /** Zmiana adresu dokonczona - konto ma nowy adres. */
        CHANGED,
        /** Nowy adres potwierdzony, brakuje jeszcze zgody ze starego. */
        WAITING_OLD,
        /** Zgoda ze starego adresu jest, brakuje potwierdzenia nowego. */
        WAITING_NEW,
        /** "To nie ja" ze starego adresu - zmiana przepadla, sesje wylogowane. */
        CHANGE_DENIED
    }
}
