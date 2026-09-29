package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adres nie moze byc ze skrzynki jednorazowej - patrz EmailAddresses. */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotDisposableEmailValidator.class)
public @interface NotDisposableEmail {

    String message() default "{validation.email.disposable}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
