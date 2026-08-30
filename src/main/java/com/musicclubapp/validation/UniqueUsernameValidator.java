package com.musicclubapp.validation;

import com.musicclubapp.repository.UserRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Walidator dla UniqueUsername - wyklad 3, slajd 65. */
public class UniqueUsernameValidator implements ConstraintValidator<UniqueUsername, String> {

    private final UserRepository userRepository;

    public UniqueUsernameValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(String username, ConstraintValidatorContext context) {
        // Puste pole to zadanie dla @NotBlank - tutaj przepuszczamy, zeby
        // uzytkownik nie dostal dwoch komunikatow o tym samym polu naraz.
        if (username == null || username.isBlank()) {
            return true;
        }
        return !userRepository.existsByUsername(username);
    }
}
