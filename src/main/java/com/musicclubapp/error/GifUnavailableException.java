package com.musicclubapp.error;

/** Usluga GIF-ow jest wylaczona (brak klucza) albo dostawca nie odpowiada. */
public class GifUnavailableException extends RuntimeException {

    private final String messageKey;

    private GifUnavailableException(String message, String messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    /** Nie ustawiono dostawcy ani klucza - GIF-y sa wylaczone. */
    public static GifUnavailableException disabled() {
        return new GifUnavailableException("GIF-y sa wylaczone (brak GIF_API_KEY)", "error.gif.disabled");
    }

    /** Dostawca odmowil albo nie odpowiedzial; {@code reason} idzie do logu (bez klucza!). */
    public static GifUnavailableException providerFailed(String reason) {
        return new GifUnavailableException("Dostawca GIF-ow: " + reason, "error.gif.unavailable");
    }

    public String getMessageKey() {
        return messageKey;
    }
}
