package com.musicclubapp.music;

/**
 * Rozpoznany link muzyczny - to, co zostaje z adresu wklejonego przez
 * uzytkownika po odsianiu wszystkiego zbednego.
 *
 * <p>Zapisujemy w bazie te trzy wartosci zamiast calego adresu. Dzieki temu
 * nie przechowujemy parametrow sledzacych (Spotify dokleja {@code ?si=},
 * YouTube potrafi doklejac {@code &list=} i inne), a adres do osadzenia
 * skladamy sami - zawsze taki sam dla tego samego nagrania.</p>
 */
public record ParsedMusicLink(
    MusicProvider provider,
    MusicKind kind,
    String externalId
) {
}
