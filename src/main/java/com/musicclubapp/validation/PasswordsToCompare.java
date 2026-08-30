package com.musicclubapp.validation;

/** Wspolny interfejs dla DTO, ktore zawieraja haslo i jego powtorzenie. */
public interface PasswordsToCompare {

    String password();

    String confirmPassword();
}
