package com.musicclubapp.dto;

import java.util.List;

/**
 * Cala gablotka playlist jednej osoby.
 *
 * <p>Ta sama budowa co {@link FavoritesResponse}: lista plus informacja,
 * czy ogladajacy moze ja zmieniac i ile pozycji sie miesci. <b>O prawie do
 * edycji decyduje serwer</b> - frontend tylko rysuje (albo nie) przyciski,
 * a prawdziwa blokada siedzi w kontrolerze, ktory w ogole nie przyjmuje
 * cudzego loginu.</p>
 */
public record PlaylistsResponse(
    List<PlaylistResponse> items,
    boolean canEdit,
    int max
) {
}
