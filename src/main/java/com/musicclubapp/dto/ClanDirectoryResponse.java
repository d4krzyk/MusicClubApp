package com.musicclubapp.dto;

import java.util.List;

/** Strona przegladarki klanow razem z gatunkami do filtra. */
public record ClanDirectoryResponse(
    List<ClanDirectoryEntry> content,
    long total,
    int page,
    int size,
    boolean last,
    /** Gatunki wystepujace w klanach z przegladarki (od najczestszych) - do listy filtra. */
    List<GenreFacet> genres
) {

    public record GenreFacet(String name, int clans) {
    }
}
