package com.musicclubapp.error;

/** Za duzo prob w krotkim czasie - na razie tylko przy wysylaniu poczty. */
public class TooManyRequestsException extends RuntimeException {

    /** Za ile sekund mozna sprobowac ponownie - trafia do naglowka Retry-After. */
    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Za duzo prob - sprobuj za " + retryAfterSeconds + " s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
