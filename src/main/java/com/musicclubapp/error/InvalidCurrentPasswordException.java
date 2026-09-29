package com.musicclubapp.error;

/** Rzucany, gdy przy zmianie hasla uzytkownik poda zle OBECNE haslo - albo nie poda go wcale. */
public class InvalidCurrentPasswordException extends RuntimeException {

    public static final String POLE = "currentPassword";

    private final String messageKey;

    public InvalidCurrentPasswordException() {
        super("Podane obecne haslo jest nieprawidlowe");
        this.messageKey = "error.password.current.invalid";
    }

    private InvalidCurrentPasswordException(String message, String messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    /** Pole hasla puste - np. przy zmianie adresu, gdzie haslo nie zawsze jest wymagane. */
    public static InvalidCurrentPasswordException missing() {
        return new InvalidCurrentPasswordException("Brak obecnego hasla", "validation.password.current.notblank");
    }

    public String getMessageKey() {
        return messageKey;
    }
}
