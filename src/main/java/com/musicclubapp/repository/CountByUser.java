package com.musicclubapp.repository;

/** "Ile czegos przypada na jedno konto" - wynik zapytania grupujacego. */
public record CountByUser(Long userId, long count) {
}
