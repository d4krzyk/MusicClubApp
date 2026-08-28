package com.musicclubapp.repository;

/**
 * Ile nieprzeczytanych wiadomosci przyszlo od jednej osoby.
 *
 * <p>Rekord, a nie interfejs-projekcja jak {@link FriendRow}, bo tu wynik
 * powstaje z <b>wyrazenia konstruktorowego</b> w JPQL
 * ({@code SELECT new ...UnreadRow(...)}). Przy zapytaniu grupujacym to
 * najprostsza droga: alternatywa jest {@code Object[]} i wyciaganie wartosci
 * po numerze kolumny, gdzie pomylka w kolejnosci nie daje bledu kompilacji,
 * tylko zla liczbe na ekranie.</p>
 *
 * @param senderId kto pisal
 * @param count    ile jego wiadomosci czeka na przeczytanie
 */
public record UnreadRow(Long senderId, long count) {
}
