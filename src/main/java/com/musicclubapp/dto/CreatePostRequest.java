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

/**
 * Dane nowego posta - czesc tekstowa.
 *
 * <p>Zdjecia przychodza osobno, jako pliki w tym samym zapytaniu
 * {@code multipart/form-data}, dlatego nie ma ich w tym rekordzie.</p>
 *
 * <p>{@link ValidMusicLink} pilnuje, zeby wklejony adres byl poprawny
 * i zgadzal sie z wybranym rodzajem - wczesniej nierozpoznany link byl
 * po prostu polykany bez slowa.</p>
 */
@ValidMusicLink
public record CreatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_CONTENT_LENGTH, message = "{validation.post.content.size}")
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
    Integer musicStartSeconds,

    /**
     * Kto ma zobaczyc ten post. {@code null} znaczy {@code PUBLIC}.
     *
     * <p><b>Domyslnie publiczny, a nie "tylko znajomi".</b> Aplikacja sluzy do
     * poznawania NOWYCH ludzi o podobnym guscie - domyslne ukrywanie wpisow
     * przed wszystkimi poza obecnymi znajomymi dzialaloby przeciwko temu,
     * po co ona w ogole jest. Kto chce inaczej, wybiera to jednym klknieciem
     * przy pisaniu.</p>
     */
    PostVisibility visibility

) implements MusicLinkToValidate {
}
