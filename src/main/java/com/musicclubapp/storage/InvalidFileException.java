package com.musicclubapp.storage;

/** Wgrany plik nie nadaje sie do przyjecia (pusty albo nie jest obrazkiem). */
public class InvalidFileException extends RuntimeException {

    private final String messageKey;

    private InvalidFileException(String message, String messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    public static InvalidFileException empty() {
        return new InvalidFileException("Wgrany plik jest pusty", "error.file.empty");
    }

    public static InvalidFileException wrongType(String type) {
        return new InvalidFileException(
            "Niedozwolony typ pliku: " + type, "error.file.type");
    }

    public String getMessageKey() {
        return messageKey;
    }
}
