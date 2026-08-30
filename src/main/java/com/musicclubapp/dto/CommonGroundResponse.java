package com.musicclubapp.dto;

import java.util.List;

/** Co dokladnie laczy ogladajacego z ta osoba. */
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
