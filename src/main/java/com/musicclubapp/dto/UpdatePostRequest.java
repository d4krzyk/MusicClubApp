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
 * Zmiana istniejacego posta: tresc i nagranie.
 *
 * <p><b>Czego tu nie ma: zdjec.</b> Dokladanie i usuwanie plikow na juz
 * opublikowanym poscie to osobny temat (trzeba by sprzatac pliki z dysku
 * i pilnowac kolejnosci), wiec na razie go nie otwieramy. Formularz mowi
 * o tym wprost, zamiast milczec.</p>
 *
 * <p>Puste {@code musicUrl} oznacza "usun nagranie z posta".</p>
 */
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

    /**
     * Kto ma widziec post po zmianie. {@code null} = zostaw jak bylo.
     *
     * <p>Zwezenie widocznosci po fakcie dziala <b>tylko na przyszlosc</b>:
     * kto post juz przeczytal, ten go przeczytal. Nie jest to wada tego
     * rozwiazania, tylko wlasciwosc kazdej publikacji.</p>
     */
    PostVisibility visibility

) implements MusicLinkToValidate {
}
