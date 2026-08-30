package com.musicclubapp.dto;

import com.musicclubapp.entity.Role;
import jakarta.validation.constraints.NotNull;

/** Zmiana roli uzytkownika przez administratora. */
public record ChangeRoleRequest(

    @NotNull(message = "{validation.role.notnull}")
    Role role

) {
}
