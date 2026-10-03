package com.musicclubapp.dto;

/** GIF dolaczony do komentarza albo wiadomosci, tak jak widzi go przegladarka. */
public record GifView(
    /** Adres pliku wyswietlanego w rozmowie (srednia wielkosc). */
    String url,
    /** Adres mniejszej wersji - na liste wyboru. */
    String previewUrl,
    int width,
    int height,
    /** Opis od dostawcy - do tekstu alternatywnego; moze byc pusty. */
    String title
) {
}
