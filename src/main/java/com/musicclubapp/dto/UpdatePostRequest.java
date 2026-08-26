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
 * Zmiana istniejacego posta: tresc i nagranie.
 *
 * <p><b>Czego tu nie ma: zdjec.</b> Dokladanie i usuwanie plikow na juz
 * opublikowanym poscie to osobny temat (trzeba by sprzatac pliki z dysku
 * i pilnowac kolejnosci), wiec na razie go nie otwieramy. Formularz mowi
 * o tym wprost, zamiast milczec.</p>
 *
 * <p>Puste {@code musicUrl} oznacza "usun nagranie z posta".</p>
 */
@PoprawnyLinkMuzyczny
public record UpdatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_DLUGOSC_TRESCI, message = "{validation.post.content.size}")
    String content,

    String musicUrl,

    MusicKind musicKind,

    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer musicStartSeconds

) implements LinkMuzycznyDoSprawdzenia {
}
