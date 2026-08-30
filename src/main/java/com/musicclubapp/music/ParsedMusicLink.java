package com.musicclubapp.music;

/**
 * Rozpoznany link muzyczny - to, co zostaje z adresu wklejonego przez uzytkownika po odsianiu
 * wszystkiego zbednego.
 */
public record ParsedMusicLink(
    MusicProvider provider,
    MusicKind kind,
    String externalId
) {
}
