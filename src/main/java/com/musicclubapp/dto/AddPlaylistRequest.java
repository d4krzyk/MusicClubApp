package com.musicclubapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dodanie playlisty do gablotki - wklejony adres i nic wiecej. */
public record AddPlaylistRequest(

    @NotBlank(message = "{validation.playlist.url.required}")
    @Size(max = 500, message = "{validation.playlist.url.size}")
    String url

) {
}
