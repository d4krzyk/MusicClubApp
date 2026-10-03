package com.musicclubapp.validation;

/** DTO wiadomosci, ktore ma byc sprawdzone pod katem "czy w ogole cos niesie". */
public interface MessageToValidate {

    String content();

    String musicUrl();

    String gif();
}
