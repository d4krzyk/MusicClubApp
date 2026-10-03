package com.musicclubapp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Odrzuca komentarz bez tekstu i bez GIF-a; blad dostaje pole "content", jak dawny {@code @NotBlank}. */
public class CommentHasContentValidator implements ConstraintValidator<CommentHasContent, CommentToValidate> {

    @Override
    public boolean isValid(CommentToValidate data, ConstraintValidatorContext context) {
        if (data == null) {
            return true;
        }
        boolean hasText = data.content() != null && !data.content().isBlank();
        boolean hasGif = data.gif() != null && !data.gif().isBlank();
        if (hasText || hasGif) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{validation.comment.notblank}")
            .addPropertyNode("content")
            .addConstraintViolation();
        return false;
    }
}
