package com.musicclubapp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Walidator dla {@link PasswordsMatch} - wyklad 3, slajd 65.
 *
 * <p>Dziala na dowolnym DTO, ktore implementuje {@link HaslaDoPorownania},
 * czyli zarowno na formularzu rejestracji, jak i na zmianie hasla
 * w ustawieniach.</p>
 */
public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, HaslaDoPorownania> {

    @Override
    public boolean isValid(HaslaDoPorownania dane, ConstraintValidatorContext context) {
        if (dane == null) {
            return true;
        }

        boolean pasuja = Objects.equals(dane.password(), dane.confirmPassword());

        if (!pasuja) {
            /*
             * Domyslnie blad walidacji na poziomie klasy nie jest przypisany do
             * zadnego pola - w odpowiedzi JSON pole "field" byloby puste i
             * frontend nie wiedzialby, co podswietlic. Ponizsze linijki
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
