package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dodanie playlisty do gablotki - wklejony adres i nic wiecej.
 *
 * <p><b>Tytul i okladka NIE przychodza z zapytania</b>, tylko sa pobierane
 * przez serwer z serwisu muzycznego. Ta sama zasada co przy ulubionych
 * artystach: gdyby nazwa pochodzila od uzytkownika, wystarczyloby wyslac
 * zapytanie z pominieciem przegladarki, zeby podpisac cudza playliste
 * czymkolwiek.</p>
 */
public record AddPlaylistRequest(

    @NotBlank(message = "{validation.playlist.url.required}")
    @Size(max = 500, message = "{validation.playlist.url.size}")
    String url

) {
}
