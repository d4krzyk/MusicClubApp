package com.musicclubapp.validation;

/**
 * Wspolny interfejs dla DTO, ktore zawieraja haslo i jego powtorzenie.
 *
 * <p><b>Po co to?</b> Adnotacja {@link PasswordsMatch} musi porownac dwa pola,
 * a jej walidator dostaje caly obiekt. Bez wspolnego interfejsu walidator
 * bylby przywiazany do jednej konkretnej klasy i przy drugim formularzu
 * (zmiana hasla) trzeba by pisac druga, prawie identyczna adnotacje.</p>
 *
 * <p>Rekordy w Javie moga implementowac interfejsy - metody {@code password()}
 * i {@code confirmPassword()} generuja sie automatycznie z nazw pol, wiec
 * wystarczy dopisac {@code implements HaslaDoPorownania}.</p>
 */
public interface HaslaDoPorownania {

    String password();

    String confirmPassword();
}
