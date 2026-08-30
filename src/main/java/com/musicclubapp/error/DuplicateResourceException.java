package com.musicclubapp.error;

/** Rzucany, gdy ktos probuje zalozyc konto na zajety login lub e-mail. */
public class DuplicateResourceException extends RuntimeException {

    private final String messageKey;

    private DuplicateResourceException(String message, String messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    public static DuplicateResourceException username(String username) {
        return new DuplicateResourceException(
            "Login jest juz zajety: " + username, "error.username.taken");
    }

    public static DuplicateResourceException email(String email) {
        return new DuplicateResourceException(
            "E-mail jest juz zajety: " + email, "error.email.taken");
    }

    public String getMessageKey() {
        return messageKey;
    }
}
