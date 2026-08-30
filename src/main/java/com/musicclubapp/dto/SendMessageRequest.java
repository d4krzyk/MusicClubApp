package com.musicclubapp.dto;

import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.Post;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.validation.MessageHasContent;
import com.musicclubapp.validation.MessageToValidate;
import com.musicclubapp.validation.MusicLinkToValidate;
import com.musicclubapp.validation.ValidMusicLink;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Nowa wiadomosc na czacie. */
@ValidMusicLink
@MessageHasContent
public record SendMessageRequest(

    /** Tresc. */
    @Size(max = Message.MAX_CONTENT_LENGTH, message = "{validation.message.size}")
    String content,

    /** Adres ze Spotify, YouTube Music albo Apple Music; pole opcjonalne. */
    String musicUrl,

    /** Co to jest: utwor, album, artysta czy playlista. Wymagane, gdy podano adres. */
    MusicKind musicKind,

    /** Sekunda, od ktorej ma zagrac utwor. */
    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer musicStartSeconds

) implements MusicLinkToValidate, MessageToValidate {
}
