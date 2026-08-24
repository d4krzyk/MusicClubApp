package com.musicclubapp.validation;

import com.musicclubapp.repository.UserRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Walidator dla {@link UniqueUsername} - wyklad 3, slajd 65.
 *
 * <p>Implementuje {@code ConstraintValidator<Adnotacja, TypPola>}, a cale
 * sprawdzenie siedzi w metodzie {@link #isValid}.</p>
 *
 * <p>Spring potrafi wstrzyknac zaleznosci do walidatora przez konstruktor,
 * dlatego mozemy tu skorzystac z repozytorium i zapytac bazy.</p>
 *
 * <p><b>Wazne:</b> ta walidacja nie zastepuje sprawdzenia w serwisie.
 * Miedzy sprawdzeniem a zapisem moze wskoczyc inne zadanie i zajac ten sam
 * login. Ostatecznym zabezpieczeniem jest ograniczenie UNIQUE na kolumnie
 * w bazie - walidacja sluzy tu do tego, zeby uzytkownik dostal ladny
 * komunikat przy polu formularza, a nie blad 500.</p>
 */
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
