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

/** Dane nowego posta - czesc tekstowa. */
@ValidMusicLink
public record CreatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_CONTENT_LENGTH, message = "{validation.post.content.size}")
    String content,

    /** Adres ze Spotify albo YouTube'a. Pole opcjonalne - post moze byc bez muzyki. */
    String musicUrl,

    /** Co to jest: utwor, album czy artysta. Wymagane, gdy podano adres. */
    MusicKind musicKind,

    /** Sekunda, od ktorej ma zagrac utwor - patrz Post#MAX_SEKUNDA_STARTU. */
    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer musicStartSeconds,

    /** Kto ma zobaczyc ten post. null znaczy PUBLIC. */
    PostVisibility visibility,

    /** Wydarzenie, pod ktorym piszemy post - albo null, gdy to zwykly wpis na tablicy. */
    Long eventId,

    /** Klan, w ktorym piszemy post (widoczny tylko dla jego czlonkow) - albo null. */
    Long clanId

) implements MusicLinkToValidate {

    /** Zwykly post na tablicy - bez wydarzenia. */
    public CreatePostRequest(String content, String musicUrl, MusicKind musicKind,
                             Integer musicStartSeconds, PostVisibility visibility) {
        this(content, musicUrl, musicKind, musicStartSeconds, visibility, null, null);
    }
}
