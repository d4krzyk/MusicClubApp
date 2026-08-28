package com.musicclubapp.repository;

/**
 * "Ile czegos przypada na jedno konto" - wynik zapytania grupujacego.
 *
 * <p>Uzywane przy liczeniu zasadnych zgloszen dla calej strony uzytkownikow
 * naraz. Wersja bez tego rekordu - policz osobno dla kazdego wiersza -
 * to przy dwudziestu kontach na stronie dwadziescia dodatkowych zapytan
 * (problem N+1), i to przy operacji, ktora z wierzchu wyglada na zwykle
 * przepisanie pol.</p>
 */
public record CountByUser(Long userId, long count) {
}
