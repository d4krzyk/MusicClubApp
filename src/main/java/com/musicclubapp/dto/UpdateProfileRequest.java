package com.musicclubapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Zmiana loginu i adresu e-mail we wlasnym profilu.
 *
 * <p><b>Dlaczego nie ma tu {@code @UniqueUsername}?</b> Ta adnotacja odrzuca
 * kazdy login, ktory juz istnieje w bazie - a przy edycji wlasnego profilu
 * uzytkownik zwykle zostawia SWOJ login bez zmian. Adnotacja uznalaby go za
 * zajety i nie dalaby zapisac niczego innego. Zajetosc sprawdzamy wiec
 * w serwisie, gdzie wiemy, kto edytuje, i mozemy pominac jego wlasny wpis.</p>
 *
 * <p>Reguly dlugosci i dozwolonych znakow sa te same co przy rejestracji -
 * inaczej dalo by sie obejsc walidacje, zmieniajac login po zalozeniu konta.</p>
 */
public record UpdateProfileRequest(

    @NotBlank(message = "{validation.username.notblank}")
    @Size(min = 3, max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "{validation.username.pattern}")
    String username,

    @NotBlank(message = "{validation.email.notblank}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    String email

) {
}
