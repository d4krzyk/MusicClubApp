package com.musicclubapp.dto;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;

import java.time.LocalDateTime;

/**
 * Jedna wiadomosc wysylana do przegladarki.
 *
 * <p>Tak samo jak przy postach: frontend dostaje <b>gotowy adres
 * odtwarzacza</b> i nie musi wiedziec, jak kazdy serwis sklada swoje adresy
 * osadzenia. Dolozenie czwartego serwisu nie wymaga wtedy ruszania Reacta.</p>
 *
 * @param mine    czy to MOJA wiadomosc - po tym frontend decyduje, czy dymek
 *                idzie na prawo, czy na lewo. Wylicza to serwer, bo tylko on
 *                wie na pewno, kto pyta; przegladarka musialaby porownywac
 *                loginy i pomylilaby sie przy zmianie wlasnego loginu
 * @param read    czy odbiorca ja przeczytal (sensowne tylko przy {@code mine})
 */
public record MessageResponse(
    Long id,
    String senderUsername,
    String senderAvatarUrl,
    /** Tresc; {@code null} albo pusta, gdy wyslano sam utwor. */
    String content,
    /** Gotowy adres odtwarzacza do {@code <iframe>} albo {@code null}. */
    String musicEmbedUrl,
    MusicProvider musicProvider,
    MusicKind musicKind,
    String musicTitle,
    String musicThumbnailUrl,
    Integer musicStartSeconds,
    /** Adres strony w serwisie - pod przycisk "otworz w serwisie". */
    String musicUrl,
    LocalDateTime createdAt,
    boolean mine,
    boolean read
) {
}
