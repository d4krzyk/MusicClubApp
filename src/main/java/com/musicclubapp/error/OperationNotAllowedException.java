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

    public String getMessageKey() {
        return messageKey;
    }
}
