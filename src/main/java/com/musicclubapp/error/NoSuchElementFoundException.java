package com.musicclubapp.error;

/**
 * Rzucany, gdy szukanego elementu nie ma w bazie - wymaganie nr 11
 * ("zwracanie wyjatkow w przypadku braku elementu w bazie").
 *
 * <p>Nazwa i zastosowanie wprost z wykladu 3 (slajdy 66 i 72):</p>
 * <pre>
 * userRepository.findById(id)
 *     .orElseThrow(() -&gt; new NoSuchElementFoundException("user", id));
 * </pre>
 *
 * <p>Dziedziczy z {@link RuntimeException}, wiec nie trzeba go deklarowac
 * w {@code throws} ani lapac w kazdej metodzie po drodze - dolatuje az do
 * {@link GlobalExceptionHandler}, ktory zamienia go na odpowiedz HTTP 404.</p>
 */
public class NoSuchElementFoundException extends RuntimeException {

    /** Klucz komunikatu w messages.properties - stad tlumaczenie PL/EN. */
    private final String messageKey;

    private final Object[] arguments;

    public NoSuchElementFoundException(String resource, Object id) {
        super("Nie znaleziono: " + resource + " o id " + id);
        this.messageKey = "error.notfound";
        /*
         * Identyfikator przekazujemy jako TEKST, nie jako liczbe. MessageFormat
         * (uzywany przez MessageSource) formatuje argumenty liczbowe wedlug
         * ustawien jezyka - dla polskiego id 9999 wyswietlaloby sie jako
         * "9 999", ze spacja oddzielajaca tysiace. Przy identyfikatorze
         * wyglada to na blad, wiec wymuszamy zapis doslowny.
         */
        this.arguments = new Object[] {resource, String.valueOf(id)};
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArguments() {
        return arguments;
    }
}
