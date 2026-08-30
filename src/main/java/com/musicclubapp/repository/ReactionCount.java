package com.musicclubapp.repository;

import com.musicclubapp.entity.ReactionType;

/** Wynik zapytania grupujacego: ile reakcji danego rodzaju zebral dany post. */
public record ReactionCount(Long postId, ReactionType type, Long count) {
}
