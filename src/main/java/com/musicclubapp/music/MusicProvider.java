package com.musicclubapp.music;

/**
 * Serwis, z ktorego pochodzi wklejony link.
 *
 * <p><b>Dlaczego nie tylko Spotify?</b> Bo aplikacja ma byc o muzyce,
 * a nie o jednej platformie. Kazdy sluchacz siedzi w innym serwisie,
 * a wymuszanie jednego oznaczaloby, ze czesc osob nie ma czym sie podzielic.</p>
 *
 * <p>Dostawce <b>rozpoznajemy z adresu</b>, nie pytamy o nia uzytkownika -
 * domena zawsze jednoznacznie mowi, skad jest link.</p>
 */
public enum MusicProvider {

    SPOTIFY,

    /**
     * YouTube Music.
     *
     * <p><b>Przyjmujemy WYLACZNIE adresy z {@code music.youtube.com}.</b>
     * Zwykly YouTube jest odrzucany - nie dlatego, ze sie nie da, tylko
     * dlatego, ze jest tam wszystko, a tablica ma byc o muzyce. Powod
     * i jego ograniczenia opisuje {@code MusicLinkParser.isPlainYouTube}.</p>
     *
     * <p><b>Technicznie to jednak jeden serwis.</b> Utwor w YouTube Music ma
     * dokladnie ten sam identyfikator filmu co na zwyklym YouTube. Osobnego
     * adresu osadzenia dla wersji muzycznej nie ma, wiec odtwarzacz zawsze
     * skladamy przez {@code youtube.com/embed/} - i z tego samego powodu
     * tytul pobieramy z oEmbed glownego YouTube'a.</p>
     *
     * <p>Albumy w YouTube Music sa technicznie PLAYLISTAMI
     * ({@code music.youtube.com/playlist?list=OLAK5uy_...}) i tak je
     * rozpoznajemy.</p>
     */
    YOUTUBE,

    /**
     * Apple Music.
     *
     * <p>Adres osadzenia powstaje przez samą PODMIANE nazwy serwera:
     * {@code music.apple.com/...} → {@code embed.music.apple.com/...}.
     * Reszta sciezki zostaje bez zmian - dlatego przy tym serwisie
     * zapisujemy cala sciezke, a nie sam identyfikator.</p>
     */
    APPLE_MUSIC
}
