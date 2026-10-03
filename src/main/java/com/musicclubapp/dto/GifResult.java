package com.musicclubapp.dto;

/** Jeden wynik wyszukiwania GIF-ow; {@code token} (podpisany) wysyla sie z komentarzem albo wiadomoscia. */
public record GifResult(
    String id,
    String title,
    String url,
    String previewUrl,
    int width,
    int height,
    String token
) {
}
