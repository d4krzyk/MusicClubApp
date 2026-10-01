package com.musicclubapp.dto;

import com.musicclubapp.entity.Clan;
import jakarta.validation.constraints.NotBlank;
import com.musicclubapp.entity.ClanJoinPolicy;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Dane nowego klanu. Zasady nazwy i skrotu sprawdza serwis - komunikaty zalezne od jezyka. */
public record CreateClanRequest(
    @NotBlank @Size(max = 64) String name,
    @NotBlank @Size(max = 16) String tag,
    @Size(max = Clan.DESCRIPTION_MAX) String description,
    /** Haslo klanu (niewymagane). */
    @Size(max = Clan.MOTTO_MAX) String motto,
    @Size(max = Clan.CITY_MAX) String city,
    /** Najwyzej trzy gatunki, ktore klan o sobie podaje. */
    @Size(max = Clan.GENRES_MAX) List<@Size(max = 60) String> genres,
    /** Brak = tylko zaproszenia. */
    ClanJoinPolicy joinPolicy,
    /** Brak = klan jest w przegladarce. */
    Boolean listed
) {
}
