package com.musicclubapp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Walidator dla MessageHasContent: odrzuca wiadomosc bez tresci, bez nagrania i bez GIF-a. */
public class MessageHasContentValidator
    implements ConstraintValidator<MessageHasContent, MessageToValidate> {

    /** Komunikat z adnotacji - czat klanu ma wlasny (nie wspomina o nagraniu). */
    private String message = "{validation.message.empty}";

    @Override
    public void initialize(MessageHasContent annotation) {
        this.message = annotation.message();
    }

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
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode("content")
            .addConstraintViolation();
        return false;
    }
}
