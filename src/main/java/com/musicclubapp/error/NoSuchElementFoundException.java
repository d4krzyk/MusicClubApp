package com.musicclubapp.error;

/**
 * Rzucany, gdy szukanego elementu nie ma w bazie - wymaganie nr 11 ("zwracanie wyjatkow w
 * przypadku braku elementu w bazie").
 */
public class NoSuchElementFoundException extends RuntimeException {

    /** Klucz komunikatu w messages.properties - stad tlumaczenie PL/EN. */
    private final String messageKey;

    private final Object[] arguments;

    public NoSuchElementFoundException(String resource, Object id) {
        super("Nie znaleziono: " + resource + " o id " + id);
        this.messageKey = "error.notfound";
        /* Identyfikator przekazujemy jako TEKST, nie jako liczbe. */
        this.arguments = new Object[] {resource, String.valueOf(id)};
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArguments() {
        return arguments;
    }
}
