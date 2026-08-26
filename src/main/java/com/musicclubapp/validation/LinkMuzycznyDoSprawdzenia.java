package com.musicclubapp.validation;

import com.musicclubapp.music.MusicKind;

/**
 * Wspolny interfejs dla DTO, ktore zawieraja link muzyczny.
 *
 * <p>Ta sama sztuczka co przy {@link HaslaDoPorownania}: adnotacja
 * {@link PoprawnyLinkMuzyczny} musi porownac ze soba TRZY pola naraz
 * (adres, wybrany rodzaj i moment startu), a walidator dostaje caly obiekt.
 * Bez wspolnego interfejsu ten sam walidator nie dzialalby jednoczesnie
 * dla dodawania i dla edycji posta.</p>
 */
public interface LinkMuzycznyDoSprawdzenia {

    String musicUrl();

    MusicKind musicKind();

    Integer musicStartSeconds();
}
