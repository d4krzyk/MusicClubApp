package com.musicclubapp.dto;

import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.validation.MusicLinkToValidate;
import com.musicclubapp.validation.ValidMusicLink;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zmiana istniejacego posta: tresc i nagranie. */
@ValidMusicLink
public record UpdatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_CONTENT_LENGTH, message = "{validation.post.content.size}")
    String content,

    String musicUrl,

    MusicKind musicKind,

    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer musicStartSeconds,

    /** Kto ma widziec post po zmianie. null = zostaw jak bylo. */
    PostVisibility visibility

) implements MusicLinkToValidate {
}
