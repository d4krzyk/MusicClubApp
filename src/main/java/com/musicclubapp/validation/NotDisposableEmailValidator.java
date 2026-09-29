package com.musicclubapp.validation;

import com.musicclubapp.service.EmailAddresses;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Walidator dla NotDisposableEmail. Pusty adres przepuszcza - to zadanie @NotBlank. */
public class NotDisposableEmailValidator implements ConstraintValidator<NotDisposableEmail, String> {

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email == null || email.isBlank() || !EmailAddresses.isDisposable(email);
    }
}
