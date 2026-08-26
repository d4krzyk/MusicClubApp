package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sprawdza, czy wklejony link muzyczny jest poprawny i czy zgadza sie
 * z wybranym rodzajem. Trzecia wlasna adnotacja w projekcie (wymaganie nr 10).
 *
 * <p><b>Po co osobna adnotacja zamiast sprawdzenia w serwisie?</b> Bo blad
 * ma trafic do KONKRETNEGO POLA formularza, a nie wyskoczyc jako ogolny
 * komunikat u gory. Bean Validation robi to za nas: frontend dostaje
 * {@code errors: [{field: "musicUrl", message: "..."}]} i podswietla wlasciwe
 * pole - tak samo jak przy zlym adresie e-mail w rejestracji.</p>
 *
 * <p>Wisi nad CALA KLASA ({@code ElementType.TYPE}), bo potrzebuje trzech pol
 * naraz - walidator pojedynczego pola widzi tylko jedna wartosc.</p>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMusicLinkValidator.class)
public @interface ValidMusicLink {

    String message() default "{validation.music.url.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
