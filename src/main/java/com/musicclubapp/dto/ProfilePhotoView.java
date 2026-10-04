package com.musicclubapp.dto;

/** Zdjecie z galerii profilu: numer (do usuwania i ukladania) i gotowy adres. */
public record ProfilePhotoView(Long id, String url) {
}
