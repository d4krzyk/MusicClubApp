package com.musicclubapp.validation;

/**
 * DTO wiadomosci, ktore ma byc sprawdzone pod katem "czy w ogole cos niesie".
 *
 * <p>Ta sama sztuczka co przy {@link MusicLinkToValidate}: adnotacja
 * {@link MessageHasContent} musi porownac ze soba DWA pola naraz (tresc
 * i link), a walidator dostaje caly obiekt. Wspolny interfejs sprawia, ze
 * walidator nie musi znac konkretnej klasy DTO - i zadziala tak samo, gdy
 * kiedys dojdzie druga (np. edycja wiadomosci).</p>
 */
public interface MessageToValidate {

    String content();

    String musicUrl();
}
