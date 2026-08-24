package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wlasna adnotacja walidacyjna - wymaganie nr 10 z listy.
 *
 * <p>Sprawdza, czy podany login nie jest juz zajety. Wbudowane adnotacje
 * ({@code @NotBlank}, {@code @Size}) tego nie potrafia, bo wymaga to zajrzenia
 * do bazy danych - i dokladnie po to robi sie wlasne adnotacje.</p>
 *
 * <p>Budowa wprost z wykladu 3, slajd 64 - trzy obowiazkowe metody:</p>
 * <ul>
 *   <li>{@code message()} - tekst zwracany przy bledzie; w klamrach podajemy
 *       klucz z pliku messages, dzieki czemu komunikat jest tlumaczony,</li>
 *   <li>{@code groups()} - do walidacji grupowej, u nas nieuzywane,</li>
 *   <li>{@code payload()} - rzadko uzywane, ale interfejs tego wymaga.</li>
 * </ul>
 *
 * <p>{@code @Constraint(validatedBy = ...)} wskazuje klase, ktora wykonuje
 * wlasciwe sprawdzenie.</p>
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueUsernameValidator.class)
public @interface UniqueUsername {

    String message() default "{validation.username.taken}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
