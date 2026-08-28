package com.musicclubapp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Walidator dla {@link MessageHasContent}: odrzuca wiadomosc bez tresci
 * <b>i</b> bez nagrania.
 *
 * <p>Blad przypinamy do pola {@code content}, a nie do calej klasy - tak samo
 * jak {@code ValidMusicLinkValidator}. Bez tego frontend dostaje komunikat
 * z pustym polem {@code field} i nie wie, co podswietlic; tutaj podswietli
 * pole tekstowe, czyli to, w ktorym uzytkownik ma cos zrobic.</p>
 */
public class MessageHasContentValidator
    implements ConstraintValidator<MessageHasContent, MessageToValidate> {

    @Override
    public boolean isValid(MessageToValidate data, ConstraintValidatorContext context) {
        if (data == null) {
            return true;
        }

        boolean hasText = data.content() != null && !data.content().isBlank();
        boolean hasMusic = data.musicUrl() != null && !data.musicUrl().isBlank();

        if (hasText || hasMusic) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{validation.message.empty}")
            .addPropertyNode("content")
            .addConstraintViolation();
        return false;
    }
}
