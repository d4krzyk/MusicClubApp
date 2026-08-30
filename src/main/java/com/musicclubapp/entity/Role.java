package com.musicclubapp.entity;

/** Rola uzytkownika w aplikacji. */
public enum Role {

    /** Zwykly uzytkownik - domyslna rola po rejestracji. */
    USER,

    /** Administrator - na razie nieuzywana, przyda sie przy moderacji postow. */
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
