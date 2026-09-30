package com.musicclubapp.dto;

/** Haslo potwierdzajace pobranie wlasnych danych. Brak hasla to osobny, czytelny blad - nie 422 z walidacji. */
public record ExportRequest(String currentPassword) {
}
