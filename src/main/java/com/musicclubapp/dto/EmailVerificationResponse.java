package com.musicclubapp.dto;

/** Wynik klikniecia w link: potwierdzone nowe konto albo zmieniony adres. */
public record EmailVerificationResponse(Result result, String username, String email) {

    public enum Result { VERIFIED, CHANGED }
}
