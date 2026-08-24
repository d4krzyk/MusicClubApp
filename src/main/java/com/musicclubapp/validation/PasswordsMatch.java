package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sprawdza, czy haslo i jego powtorzenie sa takie same - wyklad 7, slajd 35
 * ("Hasla pasuja"). Druga wlasna adnotacja w projekcie (wymaganie nr 10).
 *
 * <p><b>Roznica wzgledem {@link UniqueUsername}:</b> tamta wisi nad POLEM,
 * ta nad CALA KLASA. Musi tak byc, bo do porownania potrzebujemy dwoch pol
 * naraz, a walidator pola widzi tylko jedna wartosc. Stad
 * {@code ElementType.TYPE} zamiast {@code ElementType.FIELD}.</p>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordsMatchValidator.class)
public @interface PasswordsMatch {

    String message() default "{validation.password.mismatch}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
