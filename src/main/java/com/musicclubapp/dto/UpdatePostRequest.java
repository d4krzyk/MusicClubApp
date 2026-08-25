package com.musicclubapp.dto;

import com.musicclubapp.entity.Post;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edycja wlasnego posta.
 *
 * <p>Zmieniamy tylko tresc i utwor ze Spotify. <b>Zdjecia zostaja bez zmian</b> -
 * ich edycja wymagalaby przesylania plikow razem z informacja, ktore z obecnych
 * zachowac, w jakiej kolejnosci i ktore skasowac z dysku. Przy pomylce w doborze
 * zdjec prosciej jest usunac post i dodac go jeszcze raz.</p>
 *
 * <p>Te same reguly walidacji co przy dodawaniu - inaczej dalo by sie obejsc
 * limit dlugosci, publikujac krotki post i zaraz go rozszerzajac.</p>
 */
public record UpdatePostRequest(

    @NotBlank(message = "{validation.post.content.notblank}")
    @Size(max = Post.MAX_DLUGOSC_TRESCI, message = "{validation.post.content.size}")
    String content,

    String spotifyUrl,

    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer spotifyStartSeconds

) {
}
