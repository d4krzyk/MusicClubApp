package com.musicclubapp.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Wspolny format odpowiedzi dla wszystkich bledow zwracanych przez API.
 *
 * <p>Struktura z wykladu 3, slajd 70. Dzieki jednemu formatowi frontend
 * ma jedno miejsce do obslugi bledow - nie musi zgadywac, jak wyglada
 * odpowiedz przy kazdym rodzaju bledu.</p>
 *
 * <p>{@code @JsonInclude(NON_NULL)} sprawia, ze puste pola nie trafiaja
 * do JSON-a - odpowiedz jest krotsza i czytelniejsza.</p>
 *
 * <p>Przykladowy JSON przy bledzie walidacji:</p>
 * <pre>
 * {
 *   "status": 422,
 *   "message": "Blad walidacji. Szczegoly w polu 'errors'.",
 *   "timestamp": "2026-08-24T18:30:00",
 *   "errors": [ { "field": "email", "message": "Podaj poprawny adres e-mail" } ]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String message;
    private final LocalDateTime timestamp;

    /** Lista bledow walidacji - wypelniana tylko przy bledach z formularza. */
    private List<ValidationError> errors;

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Metoda "do dopisania" z wykladu (slajd 71) - proste dodanie do listy.
     * Lista tworzy sie dopiero przy pierwszym bledzie, zeby przy bledach
     * innych niz walidacja pole zostalo puste i nie trafilo do JSON-a.
     */
    public void addValidationError(String field, String message) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(new ValidationError(field, message));
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
