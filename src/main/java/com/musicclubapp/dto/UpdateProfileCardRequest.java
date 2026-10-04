package com.musicclubapp.dto;

import com.musicclubapp.entity.LookingFor;
import com.musicclubapp.entity.ProfilePrompt;
import com.musicclubapp.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Tekstowa czesc karty profilu - zapisywana w calosci (to, czego nie ma w zapytaniu, znika). Zdjecia maja
 * osobne adresy, bo przychodza jako pliki.
 */
public record UpdateProfileCardRequest(
    @Size(max = User.MAX_BIO, message = "{validation.card.bio.size}") String bio,
    @Size(max = LookingFor.MAX, message = "{validation.card.lookingFor.size}") List<LookingFor> lookingFor,
    @Size(max = ProfilePrompt.MAX, message = "{validation.card.prompts.size}") List<@Valid PromptAnswerRequest> prompts
) {
}
