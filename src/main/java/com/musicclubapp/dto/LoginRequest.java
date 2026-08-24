package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Dane z formularza logowania.
 *
 * <p>Tu walidacja jest celowo minimalna - sprawdzamy tylko, czy pola nie sa
 * puste. Nie ma sensu walidowac dlugosci hasla przy logowaniu: jesli ktos ma
 * stare, krotsze haslo, to i tak musi moc sie zalogowac.</p>
 */
public record LoginRequest(

    @NotBlank(message = "{validation.username.notblank}")
    String username,

    @NotBlank(message = "{validation.password.notblank}")
    String password,

    /**
     * Czy zapamietac zalogowanie (wymaganie nr 17, wyklad 7 slajdy 44-46).
     * Typ prosty {@code boolean}, a nie {@code Boolean} - gdy frontend nie
     * przysle tego pola, Jackson wstawi {@code false} zamiast {@code null}.
     */
    boolean rememberMe

) {
}
