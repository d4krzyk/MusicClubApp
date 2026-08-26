package com.musicclubapp.music;

/**
 * Serwis, z ktorego pochodzi wklejony link.
 *
 * <p><b>Dlaczego nie tylko Spotify?</b> Bo aplikacja ma byc o muzyce,
 * a nie o jednej platformie. YouTube ma najszersze pokrycie - jest tam
 * praktycznie wszystko, takze rzeczy, ktorych nie ma w zadnym serwisie
 * streamingowym.</p>
 *
 * <p>Dostawce <b>rozpoznajemy z adresu</b>, nie pytamy o nia uzytkownika -
 * domena zawsze jednoznacznie mowi, skad jest link.</p>
 */
public enum MusicProvider {

    SPOTIFY,

    /**
     * YouTube i YouTube Music.
     *
     * <p><b>To jeden serwis, nie dwa.</b> Utwor w YouTube Music ma dokladnie
     * ten sam identyfikator filmu co na zwyklym YouTube, wiec
     * {@code music.youtube.com/watch?v=XYZ} i {@code youtube.com/watch?v=XYZ}
     * wskazuja to samo nagranie. Osobnego adresu osadzenia dla YouTube Music
     * nie ma - wszystko idzie przez {@code youtube.com/embed/}.</p>
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
