package com.musicclubapp.storage;

/**
 * Wgrany plik nie nadaje sie do przyjecia (pusty albo nie jest obrazkiem).
 *
 * <p>Obslugiwany jako HTTP 422 z bledem przypietym do pola formularza,
 * dzieki czemu frontend podswietla pole wyboru pliku tak samo jak kazde inne.</p>
 */
public class InvalidFileException extends RuntimeException {

    private final String messageKey;

    private InvalidFileException(String message, String messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    public static InvalidFileException pusty() {
        return new InvalidFileException("Wgrany plik jest pusty", "error.file.empty");
    }

    public static InvalidFileException zlyTyp(String typ) {
        return new InvalidFileException(
            "Niedozwolony typ pliku: " + typ, "error.file.type");
    }

    public String getMessageKey() {
        return messageKey;
    }
}
