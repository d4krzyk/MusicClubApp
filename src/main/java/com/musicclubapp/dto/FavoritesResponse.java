package com.musicclubapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Ulubieni artysci i utwory jednej osoby - blok na profilu.
 *
 * @param artists    ulubieni wykonawcy, w kolejnosci dodawania
 * @param tracks     ulubione utwory, w kolejnosci dodawania
 * @param canEdit    czy ogladajacy moze tu cos zmienic (czyli: czy to jego profil).
 *                   Wylicza to SERWER - dorysowanie sobie przycisku w przegladarce
 *                   niczego nie odblokuje, bo kazde zapytanie i tak jest sprawdzane
 *                   ponownie
 * @param maxArtists gorny limit ulubionych wykonawcow - frontend chowa przycisk
 *                   dodawania po jego osiagnieciu
 * @param maxTracks  gorny limit ulubionych utworow
 */
@Schema(description = "Ulubieni artysci i utwory uzytkownika")
public record FavoritesResponse(
    List<CatalogArtist> artists,
    List<CatalogTrack> tracks,
    boolean canEdit,
    int maxArtists,
    int maxTracks
) {
}
