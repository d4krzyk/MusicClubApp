package com.musicclubapp.dto;

import com.musicclubapp.entity.Comment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nowy komentarz albo odpowiedz. */
public record CreateCommentRequest(

    @NotBlank(message = "{validation.comment.notblank}")
    @Size(max = Comment.MAX_LENGTH, message = "{validation.comment.size}")
    String content,

    /** Komentarz (albo odpowiedz), na ktory odpowiadam; pusty = nowy komentarz pod postem. */
    Long parentId
) {
}
