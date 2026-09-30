package com.musicclubapp.dto;

/**
 * Plakietka klanu obok loginu: skrot, kolor, ikona i tyle, ile trzeba, zeby
 * kliknac i trafic na strone klanu. Kolor idzie jako klucz i jako gotowy
 * hex - frontend nie musi znac palety.
 */
public record ClanBadge(Long id, String name, String tag, String color, String colorHex, String iconUrl) {
}
