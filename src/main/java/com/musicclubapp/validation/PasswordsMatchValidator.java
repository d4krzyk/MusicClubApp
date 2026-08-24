package com.musicclubapp.validation;

import com.musicclubapp.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Walidator dla {@link PasswordsMatch}.
 *
 * <p>Dostaje caly obiekt {@link RegisterRequest} (a nie pojedyncze pole),
 * dzieki czemu moze porownac dwie wartosci ze soba.</p>
 */
public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, RegisterRequest> {

    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean pasuja = Objects.equals(request.password(), request.confirmPassword());

        if (!pasuja) {
            /*
             * Domyslnie blad walidacji na poziomie klasy nie jest przypisany do
             * zadnego pola - w odpowiedzi JSON pole "field" byloby puste i
             * frontend nie wiedzialby, co podswietlic. Ponizsze trzy linijki
             * przypinaja komunikat do pola confirmPassword.
             */
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("confirmPassword")
                .addConstraintViolation();
        }

        return pasuja;
    }
}
