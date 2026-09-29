package com.musicclubapp.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Wspolny format odpowiedzi dla wszystkich bledow zwracanych przez API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String message;
    private final LocalDateTime timestamp;

    /** Lista bledow walidacji - wypelniana tylko przy bledach z formularza. */
    private List<ValidationError> errors;

    /** Termin, ktorego dotyczy blad - dzis wylacznie koniec zakazu. */
    private LocalDateTime deadline;

    /**
     * Staly kod bledu, po ktorym frontend rozpoznaje sytuacje niezaleznie od
     * jezyka komunikatu - np. EMAIL_NOT_VERIFIED przy logowaniu.
     */
    private String code;

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    /** Metoda "do dopisania" z wykladu (slajd 71) - proste dodanie do listy. */
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

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
