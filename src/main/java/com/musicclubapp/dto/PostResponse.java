package com.musicclubapp.dto;

import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;

import java.time.LocalDateTime;
import java.util.List;

/** Post wysylany do przegladarki. */
public record PostResponse(
    Long id,
    String authorUsername,
    String authorAvatarUrl,
    String content,
    List<String> imageUrls,
    /** Gotowy adres odtwarzacza do {@code <iframe>} albo {@code null}. */
    String musicEmbedUrl,
    /** Serwis - frontend pokazuje przy odtwarzaczu "Spotify" / "YouTube". */
    MusicProvider musicProvider,
    MusicKind musicKind,
    /** Tytul pobrany przy dodawaniu posta; moze byc {@code null}. */
    String musicTitle,
    String musicThumbnailUrl,
    Integer musicStartSeconds,
    /**
     * Adres strony w serwisie - wstawiamy go w formularz edycji i pod przycisk "otworz w
     * serwisie".
     */
    String musicUrl,
    LocalDateTime createdAt,
    /** Czy zalogowany uzytkownik moze skasowac ten post (jest autorem albo adminem). */
    boolean canDelete,
    /** Czy zalogowany uzytkownik moze edytowac ten post. */
    boolean canEdit,
    /** Liczniki reakcji i informacja, ktora z nich wybral ogladajacy. */
    ReactionSummary reactions,
    /** Publiczny czy tylko dla znajomych - frontend rysuje przy nim plakietke. */
    PostVisibility visibility,
    /** Czy autor jest w kregu ogladajacego (jego znajomym albo nim samym). */
    boolean fromFriend
) {
}
