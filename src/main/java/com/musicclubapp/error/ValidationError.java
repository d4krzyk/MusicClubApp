package com.musicclubapp.error;

/**
 * Pojedynczy blad walidacji - ktore pole i co z nim nie tak.
 * Struktura wprost z wykladu 3, slajd 70.
 */
public class ValidationError {

    private final String field;
    private final String message;

    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }
}
