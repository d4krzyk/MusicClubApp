package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanActivityMetric;
import com.musicclubapp.entity.ClanTitleMode;

/** Tytul zdefiniowany w klanie, razem z liczba osob, ktore go maja, i tym, co moze z nim zrobic ogladajacy. */
public record ClanTitleResponse(
    Long id,
    String name,
    String color,
    String colorHex,
    ClanTitleMode mode,
    ClanActivityMetric metric,
    Integer threshold,
    int holders,
    /** Ogladajacy ma ten tytul. */
    boolean mine,
    /** Ogladajacy moze go sam wziac (SELF, jeszcze go nie ma, nie przekroczy limitu). */
    boolean canClaim
) {
}
