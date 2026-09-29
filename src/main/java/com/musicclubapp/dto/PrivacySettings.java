package com.musicclubapp.dto;

import com.musicclubapp.entity.InvitePolicy;
import com.musicclubapp.entity.ProfileVisibility;
import jakarta.validation.constraints.NotNull;

/** Ustawienia prywatnosci - te same pola w odpowiedzi i przy zapisie. */
public record PrivacySettings(
    @NotNull ProfileVisibility profileVisibility,
    @NotNull InvitePolicy friendRequestsFrom,
    boolean showOnline,
    boolean showInSuggestions,
    boolean hideOnAttendeeLists
) {
}
