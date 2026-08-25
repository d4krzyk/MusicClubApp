package com.musicclubapp.dto;

import com.musicclubapp.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Zmiana roli uzytkownika przez administratora.
 *
 * <p>Typ {@link Role} zamiast zwyklego tekstu daje darmowa walidacje: Jackson
 * odrzuci wartosc spoza enuma, wiec nie da sie przyslac roli "SUPERADMIN"
 * i liczyc, ze cos sie stanie.</p>
 */
public record ChangeRoleRequest(

    @NotNull(message = "{validation.role.notnull}")
    Role role

) {
}
