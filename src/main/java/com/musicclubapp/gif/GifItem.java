package com.musicclubapp.gif;

/** Jeden GIF z wynikow dostawcy, sprowadzony do tego, czego potrzebujemy. */
public record GifItem(
    String id,
    /** Opis do tekstu alternatywnego; moze byc pusty. */
    String title,
    /** Plik pokazywany w rozmowie. */
    String url,
    /** Mniejsza wersja na liste wyboru. */
    String previewUrl,
    int width,
    int height
) {
}
