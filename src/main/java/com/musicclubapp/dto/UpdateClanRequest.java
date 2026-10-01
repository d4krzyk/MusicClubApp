package com.musicclubapp.dto;

import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanJoinPolicy;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Zmiana klanu. Nazwe i skrot zmienia tylko zalozyciel; puste pola = bez zmiany. */
public record UpdateClanRequest(
    @Size(max = 64) String name,
    @Size(max = 16) String tag,
    @Size(max = Clan.DESCRIPTION_MAX) String description,
    /** Przypiete ogloszenie - null = bez zmiany, puste = zdejmij. */
    @Size(max = Clan.ANNOUNCEMENT_MAX) String announcement,
    /** Zasady klanu - null = bez zmiany, puste = usun. */
    @Size(max = Clan.RULES_MAX) String rules,
    /** Haslo - null = bez zmiany, puste = zdejmij. */
    @Size(max = Clan.MOTTO_MAX) String motto,
    @Size(max = Clan.CITY_MAX) String city,
    /** null = bez zmiany, pusta lista = zdejmij wszystkie. */
    @Size(max = Clan.GENRES_MAX) List<@Size(max = 60) String> genres,
    ClanJoinPolicy joinPolicy,
    Boolean listed
) {
}
