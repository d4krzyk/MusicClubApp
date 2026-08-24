package com.musicclubapp.entity;

/**
 * Rola uzytkownika w aplikacji.
 *
 * <p>Typ wyliczeniowy (wyklad 1, slajdy 42-43) zamiast zwyklego tekstu -
 * dzieki temu kompilator pilnuje, ze nie wpiszemy literowki w nazwie roli.</p>
 *
 * <p>Spring Security oczekuje, ze nazwa uprawnienia zaczyna sie od
 * przedrostka {@code ROLE_} - stad metoda {@link #getAuthority()}.</p>
 */
public enum Role {

    /** Zwykly uzytkownik - domyslna rola po rejestracji. */
    USER,

    /** Administrator - na razie nieuzywana, przyda sie przy moderacji postow. */
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
