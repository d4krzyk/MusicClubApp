package com.musicclubapp.dto;

import com.musicclubapp.entity.CardVisibility;
import com.musicclubapp.entity.LookingFor;

import java.util.List;

/** Karta profilu: zdjecia, "o mnie", "szukam" i pytania muzyczne - na profilu i przy edycji. */
public record ProfileCardResponse(
    String bio,
    List<LookingFor> lookingFor,
    List<PromptAnswerView> prompts,
    List<ProfilePhotoView> photos,
    /** Kto widzi karte na profilu - tylko dla wlasciciela (innym nic do tego, null). */
    CardVisibility visibility
) {

    /** Czy na karcie cokolwiek jest - pusta nie ma sekcji na profilu. */
    public boolean isEmpty() {
        return (bio == null || bio.isBlank()) && lookingFor.isEmpty() && prompts.isEmpty() && photos.isEmpty();
    }
}
