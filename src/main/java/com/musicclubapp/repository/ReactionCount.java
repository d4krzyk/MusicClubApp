package com.musicclubapp.repository;

import com.musicclubapp.entity.ReactionType;

/**
 * Wynik zapytania grupujacego: ile reakcji danego rodzaju zebral dany post.
 *
 * <p>To nie jest encja ani DTO wysylane do przegladarki - to <b>ksztalt jednego
 * wiersza</b> zwracanego przez {@code GROUP BY}. Alternatywa byloby
 * {@code List<Object[]>} i odczytywanie kolumn po numerach
 * ({@code wiersz[0]}, {@code wiersz[1]}), co jest krotsze, ale nie do
 * odczytania i wywala sie dopiero w trakcie dzialania, gdy ktos zmieni
 * kolejnosc kolumn w zapytaniu.</p>
 */
public record ReactionCount(Long postId, ReactionType type, Long ile) {
}
