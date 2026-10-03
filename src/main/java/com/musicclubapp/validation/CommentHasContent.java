package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Komentarz musi niesc cokolwiek: tekst albo GIF. */
@Documented
@Constraint(validatedBy = CommentHasContentValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommentHasContent {

    String message() default "{validation.comment.notblank}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
