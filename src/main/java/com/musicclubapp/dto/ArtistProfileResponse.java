package com.musicclubapp.dto;

import java.util.List;

/**
 * "Kim jest" wykonawca: krotki opis z Last.fm (zwykly tekst, bez HTML-a) i adres pelnego opisu (licencja CC BY-SA
 * wymaga przypisania), liczba sluchaczy, podobni, linki. Bez klucza Last.fm albo przy jego awarii - same linki.
 * Do tego opis od Ticketmastera ({@code about}, "About" na stronie artysty): jezyk tekstu i strona artysty u nich.
 */
public record ArtistProfileResponse(String name, String bio, String bioUrl, Long listeners, List<String> similar,
                                    List<PerformerLinkView> links, String about, String aboutLang, String aboutUrl) {
}
