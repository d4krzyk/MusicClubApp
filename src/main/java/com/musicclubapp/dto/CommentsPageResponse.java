package com.musicclubapp.dto;

import java.util.List;

/** Strona komentarzy pierwszego poziomu pod postem. */
public record CommentsPageResponse(
    List<CommentResponse> content,
    int number,
    int size,
    /** Ile komentarzy pierwszego poziomu jest razem. */
    long totalElements,
    boolean last,
    /** Ile jest wszystkich komentarzy pod postem, z odpowiedziami - to liczba przy poscie. */
    long totalComments
) {
}
