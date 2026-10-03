package com.musicclubapp.dto;

import com.musicclubapp.entity.Comment;
import com.musicclubapp.validation.CommentHasContent;
import com.musicclubapp.validation.CommentToValidate;
import jakarta.validation.constraints.Size;

/** Nowy komentarz albo odpowiedz: tekst, GIF albo jedno i drugie. */
@CommentHasContent
public record CreateCommentRequest(

    @Size(max = Comment.MAX_LENGTH, message = "{validation.comment.size}")
    String content,

    /** Komentarz (albo odpowiedz), na ktory odpowiadam; pusty = nowy komentarz pod postem. */
    Long parentId,

    /** Podpisany token GIF-a z przegladarki GIF-ow ({@code GET /api/gifs/search}); pusty = bez GIF-a. */
    @Size(max = 2000, message = "{validation.gif.invalid}")
    String gif
) implements CommentToValidate {

    public CreateCommentRequest(String content, Long parentId) {
        this(content, parentId, null);
    }
}
