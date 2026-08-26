package com.musicclubapp.dto;

import com.musicclubapp.entity.Post;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.validation.LinkMuzycznyDoSprawdzenia;
import com.musicclubapp.validation.PoprawnyLinkMuzyczny;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dane nowego posta - czesc tekstowa.
 *
 * <p>Zdjecia przychodza osobno, jako pliki w tym samym zapytaniu
 * {@code multipart/form-data}, dlatego nie ma ich w tym rekordzie.</p>
 *
 * <p>{@link PoprawnyLinkMuzyczny} pilnuje, zeby wklejony adres byl poprawny
 * i zgadzal sie z wybranym rodzajem - wczesniej nierozpoznany link byl
 * po prostu polykany bez slowa.</p>
 */
@PoprawnyLinkMuzyczny
public record CreatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_DLUGOSC_TRESCI, message = "{validation.post.content.size}")
    String content,

    /** Adres ze Spotify albo YouTube'a. Pole opcjonalne - post moze byc bez muzyki. */
    String musicUrl,

    /** Co to jest: utwor, album czy artysta. Wymagane, gdy podano adres. */
    MusicKind musicKind,

    /**
     * Sekunda, od ktorej ma zagrac utwor - patrz {@link Post#MAX_SEKUNDA_STARTU}.
     * Dozwolone WYLACZNIE przy {@link MusicKind#TRACK}.
     */
    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer musicStartSeconds

) implements LinkMuzycznyDoSprawdzenia {
}
