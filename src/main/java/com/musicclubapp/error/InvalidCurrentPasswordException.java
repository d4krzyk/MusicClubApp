package com.musicclubapp.error;

/** Rzucany, gdy przy zmianie hasla uzytkownik poda zle OBECNE haslo. */
public class InvalidCurrentPasswordException extends RuntimeException {

    public static final String POLE = "currentPassword";

    public InvalidCurrentPasswordException() {
        super("Podane obecne haslo jest nieprawidlowe");
    }
}
