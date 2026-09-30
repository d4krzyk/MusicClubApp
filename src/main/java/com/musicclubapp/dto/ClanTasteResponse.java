package com.musicclubapp.dto;

import java.util.List;

/**
 * Gust klanu: wykonawcy i gatunki wspolne dla kilku czlonkow. Bez wskazywania osob; liczone sa
 * tylko profile jawne (ograniczony profil znaczy "tylko dla znajomych" - nie wchodzi do zestawienia).
 */
public record ClanTasteResponse(
    /** Czlonkowie widoczni dla ogladajacego. */
    int members,
    /** Ilu z nich wchodzi do zestawienia (jawny profil). */
    int counted,
    /** Od ilu osob wykonawca albo gatunek pojawia sie w zestawieniu. */
    int minimum,
    List<Artist> artists,
    List<Genre> genres
) {

    public record Artist(String externalId, String name, String imageUrl, long count) {
    }

    public record Genre(String name, long count) {
    }
}
