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

    YOUTUBE
}
