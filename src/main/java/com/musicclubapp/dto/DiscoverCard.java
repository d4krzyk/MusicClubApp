package com.musicclubapp.dto;

import com.musicclubapp.entity.LookingFor;

import java.util.List;

/**
 * Karta osoby w talii trybu Poznawaj. Tylko to, co ta osoba sama wystawila na karte (zdjecia, opis,
 * pytania) i to, co laczy ja z ogladajacym. Miasto i pasmo odleglosci - tylko gdy osoba pokazuje miasto.
 */
public record DiscoverCard(
    String username,
    String avatarUrl,
    /** Zdjecia z galerii, pierwsze = okladka. Pusta lista = karta z awatarem albo inicjalem. */
    List<String> photos,
    String bio,
    List<LookingFor> lookingFor,
    List<PromptAnswerView> prompts,
    String city,
    /** SAME_CITY, KM_30, KM_60, KM_120, KM_250, FAR albo null (nie wiadomo albo miasto ukryte). */
    String proximity,
    /** Dopasowanie gustu 0-4 (patrz DiscoverMatch) - od niego zalezy kolejnosc talii. */
    int tasteLevel,
    long sharedArtistCount,
    long sharedTrackCount,
    long sharedGenreCount,
    /** Kilku wspolnych wykonawcow - do pokazania na karcie. */
    List<DiscoverArtist> sharedArtists,
    List<String> sharedGenres,
    /** Pozostali ulubieni - tylko przy profilu dla wszystkich. */
    List<DiscoverArtist> otherArtists,
    long mutualFriends,
    ClanBadge clan,
    /** Konto mlodsze niz dwa tygodnie - plakietka "Nowa osoba". */
    boolean newcomer
) {
}
