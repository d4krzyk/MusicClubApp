package com.musicclubapp.error;

/**
 * Rzucany, gdy przy zmianie hasla uzytkownik poda zle OBECNE haslo.
 *
 * <p>Celowo nie uzywamy tu {@code BadCredentialsException}, ktore
 * {@code GlobalExceptionHandler} zamienia na 401. Kod 401 oznacza dla naszego
 * frontendu "sesja wygasla, pokaz ekran logowania" - a tutaj sesja jest
 * calkiem w porzadku, tylko jedno pole formularza zostalo zle wypelnione.
 * Dlatego obslugujemy to jako 422 z bledem przypietym do pola
 * {@code currentPassword}.</p>
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public static final String POLE = "currentPassword";

    public InvalidCurrentPasswordException() {
        super("Podane obecne haslo jest nieprawidlowe");
    }
}
