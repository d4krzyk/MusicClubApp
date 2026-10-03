package com.musicclubapp.gif;

import java.util.List;

/** Jedna strona wynikow; {@code next} to nieprzezroczysty znacznik kolejnej strony albo {@code null} na ostatniej. */
public record GifPage(List<GifItem> items, String next) {
}
