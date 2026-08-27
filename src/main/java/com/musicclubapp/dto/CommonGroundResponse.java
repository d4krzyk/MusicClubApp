package com.musicclubapp.dto;

import java.util.List;

/**
 * <b>Co dokladnie laczy ogladajacego z ta osoba.</b>
 *
 * <p>Do tej pory aplikacja podawala same liczby ("2 wspolnych artystow",
 * "3 wspolne gatunki"). Liczba mowi, ze cos nas laczy, ale nie mowi <i>co</i> -
 * a to wlasnie jest powod, dla ktorego ktos mialby napisac do obcej osoby.
 * "Oboje slucham Radiohead" da sie zaczac rozmowe; "2 wspolnych artystow"
 * nie da sie zaczac niczego.</p>
 *
 * <p>Puste listy sa normalnym wynikiem - wtedy interfejs mowi wprost, ze
 * na razie nic nas nie laczy.</p>
 *
 * @param self {@code true}, gdy ktos oglada wlasny profil - wtedy nie ma
 *             czego z czym porownywac i cala sekcja sie nie pokazuje
 */
public record CommonGroundResponse(
    List<CatalogArtist> artists,
    List<CatalogTrack> tracks,
    /** Nazwy gatunkow, alfabetycznie - pochodza z Last.fm przez encje artysty. */
    List<String> genres,
    List<PersonCard> friends,
    boolean self
) {

    /** Wynik dla wlasnego profilu - nie porownujemy nikogo z nim samym. */
    public static CommonGroundResponse ownProfile() {
        return new CommonGroundResponse(List.of(), List.of(), List.of(), List.of(), true);
    }

    /** Czy jest w ogole co pokazac. */
    public boolean isEmpty() {
        return artists.isEmpty() && tracks.isEmpty() && genres.isEmpty() && friends.isEmpty();
    }
}
