package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Wiadomosc musi niesc cokolwiek: tekst albo nagranie. */
@Documented
@Constraint(validatedBy = MessageHasContentValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageHasContent {

    String message() default "{validation.message.empty}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
