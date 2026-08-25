package com.musicclubapp.dto;

import com.musicclubapp.validation.HaslaDoPorownania;
import com.musicclubapp.validation.PasswordsMatch;
import com.musicclubapp.validation.UniqueUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dane przysylane przez formularz rejestracji.
 *
 * <p>To jest DTO (Data Transfer Object) - wyklad 4, slajd 23 mowi, ze do
 * {@code @RequestBody} lepiej podstawiac osobna klase niz encje. Powody:</p>
 * <ul>
 *   <li>encja ma pola, ktorych klient nie powinien ustawiac (id, rola, createdAt),</li>
 *   <li>encja trzyma {@code passwordHash}, a z formularza przychodzi zwykle haslo,</li>
 *   <li>walidacja formularza to co innego niz ograniczenia w bazie.</li>
 * </ul>
 *
 * <p>Uzywamy typu {@code record} (Java 17) - to skrocony zapis klasy, ktora
 * tylko przenosi dane. Kompilator sam generuje konstruktor, gettery,
 * {@code equals}, {@code hashCode} i {@code toString}. Pola sa niezmienne.</p>
 *
 * <p>Komunikaty bledow podajemy w klamrach jako klucze - tresc siedzi
 * w {@code lang/messages.properties} i {@code lang/messages_pl.properties},
 * dzieki czemu bledy sa po polsku albo angielsku (wymaganie nr 2).</p>
 *
 * <p>Realizuje wymaganie nr 9 (Bean Validation) oraz nr 10 - dwie wlasne
 * adnotacje: {@link UniqueUsername} na polu i {@link PasswordsMatch} na klasie.</p>
 */
@PasswordsMatch
public record RegisterRequest(

    @NotBlank(message = "{validation.username.notblank}")
    @Size(min = 3, max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "{validation.username.pattern}")
    @UniqueUsername
    String username,

    @NotBlank(message = "{validation.email.notblank}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    String email,

    /*
     * Minimum 8 znakow to rozsadne minimum. Nie wymuszamy tu cyfr i znakow
     * specjalnych - dluzsze haslo daje wiecej bezpieczenstwa niz krotkie
     * z wymuszonym "!" na koncu.
     */
    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    String password,

    /** Powtorzenie hasla - wyklad 7, slajd 35. Sprawdzane przez {@link PasswordsMatch}. */
    @NotBlank(message = "{validation.password.confirm.notblank}")
    String confirmPassword

) implements HaslaDoPorownania {
}
