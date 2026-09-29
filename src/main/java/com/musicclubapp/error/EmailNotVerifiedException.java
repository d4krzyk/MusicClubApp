package com.musicclubapp.error;

/**
 * Haslo sie zgadza, ale adres e-mail nie jest jeszcze potwierdzony.
 *
 * <p>Rzucany dopiero PO sprawdzeniu hasla. Gdyby logowanie odmawialo
 * wczesniej, ktos bez hasla moglby sprawdzac, ktore loginy naleza do
 * niepotwierdzonych kont.</p>
 */
public class EmailNotVerifiedException extends RuntimeException {

    /** Kod dla frontendu - po nim pokazuje "wyslij link ponownie". */
    public static final String CODE = "EMAIL_NOT_VERIFIED";

    private final String maskedEmail;

    public EmailNotVerifiedException(String maskedEmail) {
        super("Adres e-mail niepotwierdzony");
        this.maskedEmail = maskedEmail;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }
}
