package com.musicclubapp.dto;

import com.musicclubapp.validation.PasswordsToCompare;
import com.musicclubapp.validation.PasswordsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Zmiana wlasnego hasla.
 *
 * <p><b>Dlaczego wymagamy obecnego hasla, skoro uzytkownik jest juz
 * zalogowany?</b> Bo zalogowana sesja to nie to samo co potwierdzona
 * tozsamosc. Gdyby ktos usiadl przy niezablokowanym komputerze albo przejal
 * ciasteczko sesji, bez tego pola mogloby od razu zmienic haslo i przejac
 * konto na stale. Pytanie o obecne haslo zamyka te droge.</p>
 *
 * <p>Nazwy pol {@code password} i {@code confirmPassword} sa takie same jak
 * przy rejestracji - dzieki temu dziala tu ta sama adnotacja
 * {@link PasswordsMatch}, bez pisania drugiej, prawie identycznej.</p>
 */
@PasswordsMatch
public record ChangePasswordRequest(

    @NotBlank(message = "{validation.password.current.notblank}")
    String currentPassword,

    /** Nowe haslo. */
    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    String password,

    /** Powtorzenie nowego hasla. */
    @NotBlank(message = "{validation.password.confirm.notblank}")
    String confirmPassword

) implements PasswordsToCompare {
}
