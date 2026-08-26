package com.musicclubapp.error;

/**
 * Operacja jest technicznie mozliwa, ale zabroniona przez regule biznesowa.
 *
 * <p>Przyklad: administrator probuje odebrac uprawnienia samemu sobie.
 * Zapytanie jest poprawne, uzytkownik ma uprawnienia - po prostu nie wolno
 * tego zrobic.</p>
 *
 * <p>Obslugiwane jako HTTP 409 CONFLICT - wyklad 4 (slajd 32) opisuje ten kod
 * wlasnie jako "probe wykonania niedozwolonej operacji".</p>
 */
public class OperationNotAllowedException extends RuntimeException {

    private final String messageKey;

    private OperationNotAllowedException(String message, String messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    /**
     * Administrator nie moze zmienic wlasnej roli.
     *
     * <p>Bez tej blokady jedno klikniecie potrafiloby zamknac ostatniemu
     * adminowi droge do panelu - i nie byloby juz nikogo, kto moglby mu
     * uprawnienia przywrocic inaczej niz recznie w bazie.</p>
     */
    public static OperationNotAllowedException wlasnaRola() {
        return new OperationNotAllowedException(
            "Administrator nie moze zmienic wlasnej roli", "error.role.self");
    }

    /** Proba usuniecia posta, ktorego sie nie jest autorem (i nie jest sie adminem). */
    public static OperationNotAllowedException cudzyPost() {
        return new OperationNotAllowedException(
            "Mozna usuwac tylko wlasne posty", "error.post.notowner");
    }

    /**
     * Proba edycji cudzego posta.
     *
     * <p>Osobny komunikat od usuwania, bo tu nawet administrator nie ma prawa -
     * moderacja polega na kasowaniu, nie na przerabianiu cudzych tresci.</p>
     */
    public static OperationNotAllowedException cudzyPostEdycja() {
        return new OperationNotAllowedException(
            "Mozna edytowac tylko wlasne posty", "error.post.notauthor");
    }

    /** Proba zaproszenia samego siebie do znajomych. */
    public static OperationNotAllowedException zaproszenieDoSiebie() {
        return new OperationNotAllowedException(
            "Nie mozna zaprosic samego siebie", "error.friend.self");
    }

    /** Te osoby juz sa znajomymi - drugie zaproszenie nie ma sensu. */
    public static OperationNotAllowedException juzZnajomi() {
        return new OperationNotAllowedException(
            "Ta osoba jest juz w znajomych", "error.friend.already");
    }

    /** Zaproszenie do tej osoby juz czeka na odpowiedz. */
    public static OperationNotAllowedException zaproszenieJuzWyslane() {
        return new OperationNotAllowedException(
            "Zaproszenie zostalo juz wyslane", "error.friend.pending");
    }

    /**
     * Proba przyjecia albo odrzucenia cudzego zaproszenia.
     *
     * <p>Bez tego sprawdzenia wystarczyloby zgadnac identyfikator zaproszenia,
     * zeby zaakceptowac znajomosc miedzy dwiema obcymi osobami.</p>
     */
    public static OperationNotAllowedException cudzeZaproszenie() {
        return new OperationNotAllowedException(
            "To nie jest Twoje zaproszenie", "error.friend.notyours");
    }

    public String getMessageKey() {
        return messageKey;
    }
}
