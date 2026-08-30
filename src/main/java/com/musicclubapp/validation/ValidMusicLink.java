package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Sprawdza, czy wklejony link muzyczny jest poprawny i czy zgadza sie z wybranym rodzajem. */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMusicLinkValidator.class)
public @interface ValidMusicLink {

    String message() default "{validation.music.url.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
