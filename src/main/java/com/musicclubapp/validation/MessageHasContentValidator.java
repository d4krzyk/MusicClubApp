package com.musicclubapp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Walidator dla MessageHasContent: odrzuca wiadomosc bez tresci, bez nagrania i bez GIF-a. */
public class MessageHasContentValidator
    implements ConstraintValidator<MessageHasContent, MessageToValidate> {

    @Override
    public boolean isValid(MessageToValidate data, ConstraintValidatorContext context) {
        if (data == null) {
            return true;
        }

        boolean hasText = data.content() != null && !data.content().isBlank();
        boolean hasMusic = data.musicUrl() != null && !data.musicUrl().isBlank();

        boolean hasGif = data.gif() != null && !data.gif().isBlank();

        if (hasText || hasMusic || hasGif) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{validation.message.empty}")
            .addPropertyNode("content")
            .addConstraintViolation();
        return false;
    }
}
