package com.musicclubapp.dto;

import com.musicclubapp.entity.Post;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dane nowego posta - czesc tekstowa.
 *
 * <p>Zdjecia przychodza osobno, jako pliki w tym samym zapytaniu
 * {@code multipart/form-data}, dlatego nie ma ich w tym rekordzie.</p>
 */
public record CreatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_DLUGOSC_TRESCI, message = "{validation.post.content.size}")
    String content,

    /** Adres utworu wklejony ze Spotify. Pole opcjonalne. */
    String spotifyUrl,

    /**
     * Sekunda, od ktorej ma zagrac utwor. Gorna granica to 10 godzin -
     * wystarczy na najdluzszy utwor, a blokuje absurdalne wartosci.
     */
    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = 36000, message = "{validation.post.start.range}")
    Integer spotifyStartSeconds

) {
}
