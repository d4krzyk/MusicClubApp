package com.musicclubapp.validation;

/** DTO wiadomosci, ktore ma byc sprawdzone pod katem "czy w ogole cos niesie". */
public interface MessageToValidate {

    String content();

    /** Czat klanu nie niesie nagran - tam zawsze pusto. */
    default String musicUrl() {
        return null;
    }

    String gif();
}
