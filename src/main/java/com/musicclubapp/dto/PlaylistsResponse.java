package com.musicclubapp.dto;

import java.util.List;

/** Cala gablotka playlist jednej osoby. */
public record PlaylistsResponse(
    List<PlaylistResponse> items,
    boolean canEdit,
    int max
) {
}
