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
    List<GenreFacet> genres,
    /** Przy filtrze promienia: ile klanow pominieto, bo nie wiadomo, gdzie sa (bez miasta albo spoza listy). */
    int withoutLocation
) {

    public record GenreFacet(String name, int clans) {
    }
}
